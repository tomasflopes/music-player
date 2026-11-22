package dev.tomasflopes.musicplayer.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.tomasflopes.musicplayer.ui.theme.MusicPlayerTheme

/** Opened from Settings in debug builds (ADR 0019, 0093); there is no launcher icon for it. */
@AndroidEntryPoint
class TransitionLabActivity : ComponentActivity() {
    private val viewModel: TransitionLabViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MusicPlayerTheme { TransitionLabScreen(viewModel) } }
    }
}
