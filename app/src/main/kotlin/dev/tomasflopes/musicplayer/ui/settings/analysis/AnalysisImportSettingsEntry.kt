package dev.tomasflopes.musicplayer.ui.settings.analysis

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.settings.SettingsEntry
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import javax.inject.Inject

/**
 * Analysis in Settings (phase 3): the import from the computer and the progress, "Analysed N / M, K on phone queue".
 * The phone analyses the queue by itself in a background job (ADR 0059).
 */
class AnalysisImportSettingsEntry
    @Inject
    constructor() : SettingsEntry {
        override val id = "analysis-import"
        override val order = ORDER

        @Composable
        override fun Content() {
            val viewModel: AnalysisImportViewModel = hiltViewModel()
            val progress by viewModel.progress.collectAsStateWithLifecycle()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val picker =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    uri?.let(viewModel::onFilePicked)
                }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text("Analysis", style = MaterialTheme.typography.titleMedium)
                Text(
                    AnalysisProgressText.format(progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.running) {
                    LinearProgressIndicator(progress = { state.progress })
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !state.running,
                ) {
                    Text("Import from computer")
                }
            }
        }

        private companion object {
            /** After crossfade (100), before the debug entries (1_000 and up). */
            const val ORDER = 110
        }
    }
