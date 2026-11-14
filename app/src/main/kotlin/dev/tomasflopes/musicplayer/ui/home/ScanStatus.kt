package dev.tomasflopes.musicplayer.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.core.model.ports.ScanPhase
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress

/** The scan's progress in words, on Home. Rescan itself lives in Settings (ADR 0052). */
@Composable
fun ScanStatus(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
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
