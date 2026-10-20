package com.example.videocutter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.videocutter.theme.VideoCutterTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    val sharedVideoUri: android.net.Uri? = if (intent?.action == android.content.Intent.ACTION_SEND && intent.type?.startsWith("video/") == true) {
      intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM) as? android.net.Uri
    } else {
      null
    }

    setContent {
      VideoCutterTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation(sharedVideoUri) } }
    }
  }
}
