package com.example.videocutter

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

object VideoTrimmerHelper {

    private fun getOutputDir(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Video Cutter")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        if (result == null) {
            result = "video"
        }
        // Hapus ekstensi jika ada
        val dotIndex = result!!.lastIndexOf(".")
        if (dotIndex > 0) {
            result = result!!.substring(0, dotIndex)
        }
        // Bersihkan karakter yang tidak valid untuk nama file
        return result!!.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    // Mode 0: Potong per durasi tertentu (menjadi beberapa file sekaligus)
    fun cutAutoDuration(context: Context, videoUri: Uri, durationSec: Int, onComplete: (Boolean) -> Unit) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val totalDurationMs = durationStr?.toLongOrNull() ?: 0L
            val totalDurationSec = totalDurationMs / 1000
            
            if (totalDurationSec <= 0 || durationSec <= 0) {
                onComplete(false)
                return
            }

            val segmentCount = Math.ceil(totalDurationSec.toDouble() / durationSec).toInt()
            val baseName = getFileNameFromUri(context, videoUri)
            
            val batchId = System.currentTimeMillis()
            // Mulai potong secara rekursif (berurutan)
            trimSegment(context, videoUri, 0, durationSec, segmentCount, 0L, baseName, batchId, onComplete)

        } catch (e: Exception) {
            onComplete(false)
        } finally {
            retriever.release()
        }
    }

    private fun trimSegment(context: Context, videoUri: Uri, currentSegment: Int, durationSec: Int, totalSegments: Int, startSec: Long, baseName: String, batchId: Long, onComplete: (Boolean) -> Unit) {
        if (currentSegment >= totalSegments) {
            onComplete(true) // Selesai semua
            return
        }

        val endSec = startSec + durationSec
        val outFile = File(getOutputDir(), "${baseName}_bagian_${currentSegment + 1}_${batchId}.mp4").absolutePath
        
        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startSec * 1000)
                    .setEndPositionMs(endSec * 1000)
                    .build()
            ).build()

        val transformer = Transformer.Builder(context)
            .experimentalSetTrimOptimizationEnabled(true)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    // Lanjut ke bagian berikutnya
                    trimSegment(context, videoUri, currentSegment + 1, durationSec, totalSegments, endSec, baseName, batchId, onComplete)
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    onComplete(false) // Gagal
                }
            })
            .build()
            
        transformer.start(mediaItem, outFile)
    }

    // Mode 1 & 2: Buang sisa (hanya ambil rentang Start - Stop)
    fun trimVideoDiscardRemainder(context: Context, videoUri: Uri, startSec: Long, endSec: Long, onComplete: (Boolean) -> Unit) {
        val baseName = getFileNameFromUri(context, videoUri)
        val outFile = File(getOutputDir(), "${baseName}_potongan_${System.currentTimeMillis()}.mp4").absolutePath
        
        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startSec * 1000)
                    .setEndPositionMs(endSec * 1000)
                    .build()
            ).build()

        val transformer = Transformer.Builder(context)
            .experimentalSetTrimOptimizationEnabled(true)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onComplete(true)
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    onComplete(false)
                }
            })
            .build()
            
        transformer.start(mediaItem, outFile)
    }
    
    // Mode 1: Simpan sisa (Bagian sebelum start, dan sesudah stop)
    fun trimVideoKeepRemainder(context: Context, videoUri: Uri, startSec: Long, endSec: Long, onComplete: (Boolean) -> Unit) {
        val baseName = getFileNameFromUri(context, videoUri)
        val timeId = System.currentTimeMillis()
        val out1 = File(getOutputDir(), "${baseName}_sisa_awal_${timeId}.mp4").absolutePath
        val out2 = File(getOutputDir(), "${baseName}_potongan_tengah_${timeId}.mp4").absolutePath
        
        // Ekspor Sisa Awal
        val itemAwal = MediaItem.Builder().setUri(videoUri)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(startSec * 1000).build()).build()
        
        val transformerAwal = Transformer.Builder(context)
            .experimentalSetTrimOptimizationEnabled(true)
            .addListener(object: Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                // Setelah awal selesai, ekspor Tengah
                val itemTengah = MediaItem.Builder().setUri(videoUri)
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startSec * 1000)
                        .setEndPositionMs(endSec * 1000).build()).build()
                        
                val transformerTengah = Transformer.Builder(context)
                    .experimentalSetTrimOptimizationEnabled(true)
                    .addListener(object: Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        onComplete(true)
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        onComplete(false)
                    }
                }).build()
                transformerTengah.start(itemTengah, out2)
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                onComplete(false)
            }
        }).build()
        
        transformerAwal.start(itemAwal, out1)
    }
}
