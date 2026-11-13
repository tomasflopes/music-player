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

/** Play (in order) and Shuffle for a list of songs; both are disabled while there is nothing to play. */
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
        Button(onClick = onPlay, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(Spacing.xxs))
            Text("Play")
        }
        FilledTonalButton(onClick = onShuffle, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Shuffle, contentDescription = null)
            Spacer(Modifier.width(Spacing.xxs))
            Text("Shuffle")
        }
    }
}
