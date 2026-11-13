package dev.tomasflopes.musicplayer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.core.model.ports.ScanPhase
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress

/** The scan's progress in words, plus Rescan; the button waits while a scan is running. */
@Composable
fun ScanStatus(
    scan: ScanProgress,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = scan.label().orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onRescan, enabled = !scan.isRunning()) { Text("Rescan") }
    }
}

/** One scan runs through several phases, each counting from 0; naming the phase stops that looking like rescans. */
internal fun ScanProgress.label(): String? =
    when (phase) {
        ScanPhase.LISTING -> "Looking for files…"
        ScanPhase.READING_TAGS -> "Reading tags $done/$total"
        ScanPhase.INDEXING -> "Saving $done/$total"
        ScanPhase.FAILED -> "Scan failed"
        ScanPhase.IDLE, ScanPhase.DONE -> null
    }

internal fun ScanProgress.isRunning(): Boolean =
    phase == ScanPhase.LISTING || phase == ScanPhase.READING_TAGS || phase == ScanPhase.INDEXING
