package dev.tomasflopes.musicplayer.debug

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Debug → Analysis benchmark: median and p95 per stage, the sample rates found and an estimate for 16,000 songs. */
@Composable
fun AnalysisBenchmarkScreen(viewModel: AnalysisBenchmarkViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Analysis benchmark", style = MaterialTheme.typography.titleMedium)
        Text("60 songs from the library, fixed seed: full decode, seek-decode and the spectrum stub (ADR 0054).")
        Button(onClick = viewModel::start, enabled = !state.running) {
            Text(if (state.running) "Running" else "Run benchmark")
        }
        if (state.progress.isNotEmpty()) Text(state.progress)
        if (state.report.isNotEmpty()) {
            Text(
                state.report,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}
