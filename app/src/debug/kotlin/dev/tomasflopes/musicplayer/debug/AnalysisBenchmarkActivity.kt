package dev.tomasflopes.musicplayer.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.tomasflopes.musicplayer.ui.theme.MusicPlayerTheme

/** Opened from Settings in debug builds (ADR 0019); there is no launcher icon for it. */
@AndroidEntryPoint
class AnalysisBenchmarkActivity : ComponentActivity() {
    private val viewModel: AnalysisBenchmarkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MusicPlayerTheme { AnalysisBenchmarkScreen(viewModel) } }
    }
}
