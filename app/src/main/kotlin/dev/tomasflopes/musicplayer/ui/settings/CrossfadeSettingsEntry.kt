package dev.tomasflopes.musicplayer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import javax.inject.Inject
import kotlin.math.roundToInt

private const val MS_PER_SECOND = 1_000f

/** Crossfade in Settings (phase 6, ADR 0019): an on/off switch and, while it is on, a one to twelve second slider. */
class CrossfadeSettingsEntry
    @Inject
    constructor() : SettingsEntry {
        override val id = "crossfade"
        override val order = ORDER

        @Composable
        override fun Content() {
            val viewModel: CrossfadeSettingsViewModel = hiltViewModel()
            val settings by viewModel.state.collectAsStateWithLifecycle()
            CrossfadeRow(settings, viewModel::onEnabled, viewModel::onDurationSeconds, viewModel::onStyle)
        }

        private companion object {
            /** After the built-in rows, before the debug entries (1_000 and up). */
            const val ORDER = 100
        }
    }

@Composable
private fun CrossfadeRow(
    settings: CrossfadeSettings,
    onEnabled: (Boolean) -> Unit,
    onDurationSeconds: (Int) -> Unit,
    onStyle: (TransitionStyle) -> Unit,
) {
    val savedSeconds = settings.durationMs / MS_PER_SECOND
    var dragging by remember(savedSeconds) { mutableFloatStateOf(savedSeconds) }
    val range = CrossfadeSettings.DURATION_RANGE_MS
    val secondsRange = (range.first / MS_PER_SECOND)..(range.last / MS_PER_SECOND)
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Crossfade", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (settings.enabled) "${dragging.roundToInt()} s between songs" else "Off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = onEnabled)
        }
        if (settings.enabled) {
            Slider(
                value = dragging,
                onValueChange = { dragging = it },
                onValueChangeFinished = { onDurationSeconds(dragging.roundToInt()) },
                valueRange = secondsRange,
                steps = (secondsRange.endInclusive - secondsRange.start).roundToInt() - 1,
            )
            StyleChips(settings.style, onStyle)
        }
    }
}

/** The style of a transition: the phase 6 crossfade, or beat-matched DJ mixes that fall back to the crossfade. */
@Composable
private fun StyleChips(
    style: TransitionStyle,
    onStyle: (TransitionStyle) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FilterChip(
            selected = style == TransitionStyle.CROSSFADE,
            onClick = { onStyle(TransitionStyle.CROSSFADE) },
            label = { Text("Crossfade") },
        )
        FilterChip(
            selected = style == TransitionStyle.DJ_MIX,
            onClick = { onStyle(TransitionStyle.DJ_MIX) },
            label = { Text("DJ mix") },
        )
    }
}
