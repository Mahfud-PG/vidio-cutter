package com.example.videocutter.ui.main

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation3.runtime.NavKey
import com.example.videocutter.VideoTrimmerHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  initialVideoUri: Uri? = null
) {
  val context = LocalContext.current
  var videoUri by remember { mutableStateOf<Uri?>(initialVideoUri) }
  var cutMode by remember { mutableIntStateOf(0) } // 0: Otomatis, 1: Start/Stop, 2: Manual
  
  // Mode 0 states
  var durationInput by remember { mutableStateOf("") }
  
  // Mode 1 states
  var startMarkerMs by remember { mutableLongStateOf(0L) }
  var stopMarkerMs by remember { mutableLongStateOf(0L) }
  
  // Mode 2 states
  var startJam by remember { mutableStateOf("") }
  var startMenit by remember { mutableStateOf("") }
  var startDetik by remember { mutableStateOf("") }
  
  var stopJam by remember { mutableStateOf("") }
  var stopMenit by remember { mutableStateOf("") }
  var stopDetik by remember { mutableStateOf("") }

  // ExoPlayer for Mode 1
  val exoPlayer = remember { 
      val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
          .setUsage(androidx.media3.common.C.USAGE_MEDIA)
          .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
          .build()
      ExoPlayer.Builder(context).build().apply {
          setAudioAttributes(audioAttributes, true)
      }
  }

  DisposableEffect(videoUri) {
    if (videoUri != null) {
        val mediaItem = MediaItem.fromUri(videoUri!!)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = false
    }
    onDispose { 
        exoPlayer.release() 
    }
  }

  // videoPickerLauncher dihapus sesuai permintaan

  var isProcessing by remember { mutableStateOf(false) }
  var showMode1Dialog by remember { mutableStateOf(false) }

  // Jaga layar agar tetap hidup (Keep Screen On) saat isProcessing = true
  val currentView = LocalView.current
  DisposableEffect(isProcessing) {
      currentView.keepScreenOn = isProcessing
      onDispose {
          currentView.keepScreenOn = false
      }
  }

  fun executeTrimming(keepRemainder: Boolean = false) {
      if (videoUri == null) {
          Toast.makeText(context, "Silakan pilih video terlebih dahulu", Toast.LENGTH_SHORT).show()
          return
      }

      isProcessing = true
      val onComplete: (Boolean) -> Unit = { success ->
          isProcessing = false
          val msg = if (success) "Video berhasil dipotong dan disimpan di folder DCIM/Video Cutter" else "Gagal memotong video"
          Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

          if (success) {
              try {
                  val ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                  val ringtone = android.media.RingtoneManager.getRingtone(context, ringtoneUri)
                  ringtone.play()

                  val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                      vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                  } else {
                      @Suppress("DEPRECATION")
                      vibrator.vibrate(500)
                  }
              } catch (e: Exception) {
                  // ignore
              }
          }
      }

      try {
          when (cutMode) {
              0 -> {
                  val durSec = durationInput.toIntOrNull() ?: 0
                  if (durSec <= 0) {
                      Toast.makeText(context, "Masukkan durasi yang valid", Toast.LENGTH_SHORT).show()
                      isProcessing = false
                      return
                  }
                  VideoTrimmerHelper.cutAutoDuration(context, videoUri!!, durSec, onComplete)
              }
              1 -> {
                  val startSec = startMarkerMs / 1000
                  val stopSec = stopMarkerMs / 1000
                  if (stopSec <= startSec) {
                      Toast.makeText(context, "Waktu stop harus lebih besar dari start", Toast.LENGTH_SHORT).show()
                      isProcessing = false
                      return
                  }
                  if (keepRemainder) {
                      VideoTrimmerHelper.trimVideoKeepRemainder(context, videoUri!!, startSec, stopSec, onComplete)
                  } else {
                      VideoTrimmerHelper.trimVideoDiscardRemainder(context, videoUri!!, startSec, stopSec, onComplete)
                  }
              }
              2 -> {
                  val sJam = startJam.toIntOrNull() ?: 0
                  val sMenit = startMenit.toIntOrNull() ?: 0
                  val sDetik = startDetik.toIntOrNull() ?: 0
                  
                  val eJam = stopJam.toIntOrNull() ?: 0
                  val eMenit = stopMenit.toIntOrNull() ?: 0
                  val eDetik = stopDetik.toIntOrNull() ?: 0

                  val startSec = (sJam * 3600) + (sMenit * 60) + sDetik
                  val stopSec = (eJam * 3600) + (eMenit * 60) + eDetik

                  val totalSec = (exoPlayer.duration / 1000).toInt()
                  if (totalSec > 0 && stopSec > totalSec) {
                      Toast.makeText(context, "Waktu selesai melebihi durasi total video ($totalSec detik)", Toast.LENGTH_SHORT).show()
                      isProcessing = false
                      return
                  }
                  if (stopSec <= startSec) {
                      Toast.makeText(context, "Waktu selesai harus lebih besar dari waktu mulai", Toast.LENGTH_SHORT).show()
                      isProcessing = false
                      return
                  }
                  VideoTrimmerHelper.trimVideoDiscardRemainder(context, videoUri!!, startSec.toLong(), stopSec.toLong(), onComplete)
              }
          }
      } catch (e: Exception) {
          isProcessing = false
          Toast.makeText(context, "Terjadi kesalahan: ${e.message}", Toast.LENGTH_LONG).show()
      }
  }

  if (showMode1Dialog) {
      AlertDialog(
          onDismissRequest = { showMode1Dialog = false },
          title = { Text("Simpan Sisa Video?") },
          text = { Text("Apakah Anda ingin membuang sisa video (sebelum Start dan sesudah Stop), atau menyimpannya sebagai file terpisah?") },
          confirmButton = {
              Button(onClick = { 
                  showMode1Dialog = false
                  executeTrimming(keepRemainder = true) 
              }, modifier = Modifier.semantics { contentDescription = "Simpan sisanya menjadi file terpisah" }) {
                  Text("Simpan Sisanya")
              }
          },
          dismissButton = {
              Button(onClick = { 
                  showMode1Dialog = false
                  executeTrimming(keepRemainder = false) 
              }, modifier = Modifier.semantics { contentDescription = "Buang sisa video" }) {
                  Text("Buang Sisanya")
              }
          }
      )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Video Cutter Aksesibel") }
      )
    }
  ) { paddingValues ->
    Column(
      modifier = modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Tombol Pilih Video dihapus

      // GLOBAL VIDEO PLAYER
      if (videoUri != null) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              AndroidView(
                  factory = { ctx ->
                      PlayerView(ctx).apply {
                          player = exoPlayer
                          useController = false // Sembunyikan kontrol bawaan agar tidak mengganggu TalkBack
                      }
                  },
                  modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(12.dp)).semantics { contentDescription = "Pemutar video" }
              )
              
              var isPlaying by remember { mutableStateOf(false) }
              var currentPosMs by remember { mutableLongStateOf(0L) }

              LaunchedEffect(isPlaying) {
                  while (isPlaying) {
                      currentPosMs = exoPlayer.currentPosition
                      kotlinx.coroutines.delay(250)
                  }
              }

              val totalDurSec = (exoPlayer.duration.coerceAtLeast(0)) / 1000
              val posSec = currentPosMs / 1000
              val descText = if (totalDurSec > 0) "Posisi: $posSec detik dari total $totalDurSec detik" else "Posisi video: $posSec detik"
              
              Text(
                  text = descText,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  modifier = Modifier
                      .fillMaxWidth()
                      .semantics { contentDescription = descText }
              )

              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  FilledTonalButton(
                      onClick = { 
                          val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                          exoPlayer.seekTo(newPos)
                          currentPosMs = newPos
                      },
                      modifier = Modifier.semantics { contentDescription = "Mundur 10 detik" }
                  ) {
                      Text("<< 10d")
                  }
                  
                  Button(
                      onClick = { 
                          if (isPlaying) {
                              exoPlayer.pause()
                          } else {
                              exoPlayer.play()
                          }
                          isPlaying = !isPlaying
                          currentPosMs = exoPlayer.currentPosition
                      }, 
                      modifier = Modifier.semantics { contentDescription = if (isPlaying) "Stop Video" else "Play Video" }
                  ) {
                      Text(if (isPlaying) "Stop" else "Play")
                  }

                  FilledTonalButton(
                      onClick = { 
                          exoPlayer.seekTo(exoPlayer.currentPosition + 10000)
                          currentPosMs = exoPlayer.currentPosition
                      },
                      modifier = Modifier.semantics { contentDescription = "Maju 10 detik" }
                  ) {
                      Text("10d >>")
                  }
              }

              Button(
                  onClick = { 
                      videoUri = null
                      durationInput = ""
                      startMarkerMs = 0L; stopMarkerMs = 0L
                      startJam = ""; startMenit = ""; startDetik = ""
                      stopJam = ""; stopMenit = ""; stopDetik = ""
                  },
                  modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Bersihkan video dan reset pengaturan" },
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
              ) {
                  Text("Bersihkan Layar")
              }
          }
        }
      } else {
          Text(
              "Buka File Manager atau Galeri Anda, pilih video, lalu bagikan (Kirim/Share) ke aplikasi VidioCutter ini untuk mulai memotongnya.", 
              modifier = Modifier.semantics { contentDescription = "Buka File Manager atau Galeri Anda, pilih video, lalu bagikan atau Kirim ke aplikasi VidioCutter ini untuk mulai memotongnya." },
              style = MaterialTheme.typography.bodyLarge
          )
      }

      ElevatedCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
              val options = listOf("1. Potong Otomatis per Durasi", "2. Potong dengan Tombol Start & Stop", "3. Potong Manual (Masukkan Waktu)")
              var expanded by remember { mutableStateOf(false) }

              ExposedDropdownMenuBox(
                  expanded = expanded,
                  onExpandedChange = { expanded = !expanded }
              ) {
                  OutlinedTextField(
                      modifier = Modifier.menuAnchor().fillMaxWidth().semantics { contentDescription = "Pilih mode pemotongan video" },
                      readOnly = true,
                      value = options[cutMode],
                      onValueChange = {},
                      label = { Text("Mode Pemotongan") },
                      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                  )
                  ExposedDropdownMenu(
                      expanded = expanded,
                      onDismissRequest = { expanded = false }
                  ) {
                      options.forEachIndexed { index, selectionOption ->
                          DropdownMenuItem(
                              text = { Text(selectionOption) },
                              onClick = {
                                  cutMode = index
                                  expanded = false
                              }
                          )
                      }
                  }
              }

              // Mode Specific UI
              when (cutMode) {
                0 -> {
                  OutlinedTextField(
                    value = durationInput,
                    onValueChange = { durationInput = it },
                    label = { Text("Durasi per potongan (detik)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                      .fillMaxWidth()
                      .semantics { contentDescription = "Kotak input. Masukkan durasi dalam hitungan detik. Contoh: 30 untuk memotong per 30 detik." }
                  )
                }
                1 -> {
                  Text("Waktu Start Terpilih: ${startMarkerMs / 1000} detik", modifier = Modifier.semantics { contentDescription = "Waktu Start Terpilih adalah ${startMarkerMs / 1000} detik" })
                  Text("Waktu Stop Terpilih: ${stopMarkerMs / 1000} detik", modifier = Modifier.semantics { contentDescription = "Waktu Stop Terpilih adalah ${stopMarkerMs / 1000} detik" })

                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilledTonalButton(onClick = { 
                        startMarkerMs = exoPlayer.currentPosition
                    }, modifier = Modifier.semantics { contentDescription = "Tandai Start. Menandai waktu mulai potongan video saat ini." }) {
                      Text("Tandai Start")
                    }
                    FilledTonalButton(onClick = { 
                        stopMarkerMs = exoPlayer.currentPosition
                    }, modifier = Modifier.semantics { contentDescription = "Tandai Stop. Menandai waktu akhir potongan video saat ini." }) {
                      Text("Tandai Stop")
                    }
                  }
                }
                2 -> {
                  Text("Waktu Mulai:", style = MaterialTheme.typography.titleSmall)
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startJam, onValueChange = { startJam = it }, label = { Text("Jam") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f).semantics { contentDescription = "Kotak input jam waktu mulai" })
                    OutlinedTextField(value = startMenit, onValueChange = { startMenit = it }, label = { Text("Menit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f).semantics { contentDescription = "Kotak input menit waktu mulai" })
                    OutlinedTextField(value = startDetik, onValueChange = { startDetik = it }, label = { Text("Detik") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f).semantics { contentDescription = "Kotak input detik waktu mulai" })
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text("Waktu Selesai:", style = MaterialTheme.typography.titleSmall)
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = stopJam, onValueChange = { stopJam = it }, label = { Text("Jam") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f).semantics { contentDescription = "Kotak input jam waktu selesai" })
                    OutlinedTextField(value = stopMenit, onValueChange = { stopMenit = it }, label = { Text("Menit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f).semantics { contentDescription = "Kotak input menit waktu selesai" })
                    OutlinedTextField(value = stopDetik, onValueChange = { stopDetik = it }, label = { Text("Detik") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f).semantics { contentDescription = "Kotak input detik waktu selesai" })
                  }
                }
              }
          }
      }

      Spacer(modifier = Modifier.height(32.dp))

      // Single Trim Button
      Button(
        onClick = { 
            if (cutMode == 1) {
                showMode1Dialog = true
            } else {
                executeTrimming()
            }
        },
        enabled = !isProcessing,
        modifier = Modifier
          .fillMaxWidth()
          .height(72.dp)
          .semantics { contentDescription = if (isProcessing) "Sedang memproses pemotongan video, mohon tunggu" else "Tombol Eksekusi. Mulai Potong Video. Pastikan Anda telah memilih video dan mengatur parameter pemotongan sebelum menekan tombol ini." },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
      ) {
        Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(if (isProcessing) "MEMPROSES..." else "POTONG VIDEO", style = MaterialTheme.typography.titleLarge)
      }

      Spacer(modifier = Modifier.height(16.dp))
      
      // Company Name
      Text(
          text = "by Ajang Berbagi",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
              .align(Alignment.CenterHorizontally)
              .semantics { contentDescription = "Dibuat oleh Ajang Berbagi" }
      )
    }
  }
}
