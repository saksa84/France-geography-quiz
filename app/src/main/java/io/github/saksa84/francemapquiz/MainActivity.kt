package io.github.saksa84.francemapquiz

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.saksa84.francemapquiz.ui.FranceMapQuizApp
import io.github.saksa84.francemapquiz.ui.theme.FranceMapQuizTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Les boutons de volume règlent le volume des médias, celui des sons de réponse.
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge()
        setContent {
            FranceMapQuizTheme {
                FranceMapQuizApp()
            }
        }
    }
}
