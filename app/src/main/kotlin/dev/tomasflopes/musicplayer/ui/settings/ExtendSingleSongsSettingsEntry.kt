package dev.tomasflopes.musicplayer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import javax.inject.Inject

/** "Extend single songs" in Settings (ADR 0080): a song chosen in Search plays, then similar songs follow it. */
class ExtendSingleSongsSettingsEntry
    @Inject
    constructor() : SettingsEntry {
        override val id = "extend-single-songs"
        override val order = ORDER

        @Composable
        override fun Content() {
            val viewModel: ExtendSingleSongsSettingsViewModel = hiltViewModel()
            val enabled by viewModel.enabled.collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Extend single songs", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled) "A song picked in Search is followed by similar songs" else "Off: it plays alone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = viewModel::onEnabled)
            }
        }

        private companion object {
            /** Right after Crossfade (100), before the debug entries (1_000 and up). */
            const val ORDER = 110
        }
    }
