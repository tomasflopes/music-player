package dev.tomasflopes.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import dev.tomasflopes.musicplayer.ui.theme.MusicPlayerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicPlayerTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Music Player", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}
