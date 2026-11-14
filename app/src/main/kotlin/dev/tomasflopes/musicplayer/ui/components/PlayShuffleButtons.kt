package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/**
 * Shuffle and Play in order for a list of songs (ADR 0048): Shuffle is the primary, filled button and comes first;
 * Play in order is the secondary one. Both are disabled while there is nothing to play.
 */
@Composable
fun PlayShuffleButtons(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        ShuffleButton(onShuffle, Modifier.weight(1f), enabled)
        PlayInOrderButton(onPlay, Modifier.weight(1f), enabled)
    }
}

/** The primary action of a list: play it shuffled. */
@Composable
fun ShuffleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(Icons.Default.Shuffle, contentDescription = null)
        Spacer(Modifier.width(Spacing.xxs))
        Text("Shuffle")
    }
}

/** The secondary action of a list: play it in its own order. */
@Composable
fun PlayInOrderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(Spacing.xxs))
        Text("Play in order")
    }
}
