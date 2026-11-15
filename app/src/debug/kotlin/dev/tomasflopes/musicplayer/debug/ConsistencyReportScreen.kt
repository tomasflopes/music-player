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

/** Debug → Consistency report: the largest gap per v1 feature between the Mac profile and this phone's (ADR 0060). */
@Composable
fun ConsistencyReportScreen(viewModel: ConsistencyReportViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Consistency report", style = MaterialTheme.typography.titleMedium)
        Text("Re-analyses 20 imported songs on this phone and compares them with the Mac profile.")
        Button(onClick = viewModel::start, enabled = !state.running) {
            Text(if (state.running) "Running" else "Run report")
        }
        if (state.progress.isNotEmpty() && state.running) Text(state.progress)
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
