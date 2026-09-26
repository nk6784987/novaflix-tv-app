package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.data.local.CineStreamDatabase
import com.example.data.repository.MediaRepository
import com.example.ui.components.TvEnvironment
import com.example.ui.components.isTelevisionDevice
import com.example.ui.navigation.AppNavGraph
import com.example.ui.theme.CineStreamTheme
import com.example.ui.theme.PitchBlack
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    try {
      FirebaseApp.initializeApp(this)
    } catch (e: Throwable) {
      Log.w("MainActivity", "FirebaseApp initialization note: ${e.message}")
    }

    // keep the Realtime Database cached on disk + queue writes while offline
    // (must run before the database is used anywhere else)
    try {
      com.google.firebase.database.FirebaseDatabase
        .getInstance(com.example.data.repository.FirebaseRepository.DB_URL)
        .setPersistenceEnabled(true)
    } catch (e: Throwable) {
      Log.w("MainActivity", "RTDB persistence note: ${e.message}")
    }

    val prefs = com.example.data.repository.AppPrefs.get(this)
    com.example.data.repository.ServerConfig.init(prefs)

    val database = CineStreamDatabase.getDatabase(this)
    val mediaRepository = MediaRepository(
      database.watchItemDao(),
      prefs = prefs
    )

    // Android TV / Google TV / Fire TV (or "TV mode" forced from Profile for testing)
    val isTv = isTelevisionDevice(this) || prefs.forceTvMode

    setContent {
      CineStreamTheme {
        TvEnvironment(isTv = isTv) {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = PitchBlack
          ) {
            val navController = rememberNavController()
            AppNavGraph(
              navController = navController,
              mediaRepository = mediaRepository
            )
          }
        }
      }
    }
  }
}
