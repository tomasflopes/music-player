package dev.tomasflopes.musicplayer.ui.home.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.home.HomeSection
import dev.tomasflopes.musicplayer.ui.settings.SettingsRoute
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import javax.inject.Inject

/**
 * Onboarding step 4 (core design 7.0, phase 3): "Have an analysis file from your computer?". It shows on Home while
 * nothing is analysed and the phone queue is long, until the owner closes it (rule in `AnalysisHintRule`).
 */
class AnalysisHintSection
    @Inject
    constructor() : HomeSection {
        override val id = "analysis_hint"
        override val order = 5

        @Composable
        override fun Content(onNavigate: (String) -> Unit) {
            val viewModel: AnalysisHintViewModel = hiltViewModel()
            val show by viewModel.show.collectAsStateWithLifecycle()
            if (show) {
                AnalysisHintCard(
                    onOpenSettings = { onNavigate(SettingsRoute.ROUTE) },
                    onDismiss = viewModel::dismiss,
                )
            }
        }
    }

@Composable
private fun AnalysisHintCard(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xxs)) {
        Column(Modifier.padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                "Have an analysis file from your computer? Import it in Settings, Analysis.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}
