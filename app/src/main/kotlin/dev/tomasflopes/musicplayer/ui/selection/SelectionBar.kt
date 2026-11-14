package dev.tomasflopes.musicplayer.ui.selection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.playlists.AddTarget
import dev.tomasflopes.musicplayer.ui.playlists.AddToPlaylistSheet
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/**
 * Shown above a list while [selection] is active: how many are ticked, Close, and Add to playlist for the ticked
 * songs of [songs] (in list order). Back ends the selection first. Draws nothing when there is no selection.
 */
@Composable
fun SelectionBar(
    selection: SelectionState,
    songs: List<Song>,
    modifier: Modifier = Modifier,
) {
    if (!selection.active) return
    BackHandler { selection.clear() }
    var adding by remember { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                ).padding(horizontal = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = selection::clear) { Icon(Icons.Default.Close, contentDescription = "Clear selection") }
        Text(
            "${selection.count} selected",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { adding = true }) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
            Text("Add to playlist", modifier = Modifier.padding(start = Spacing.xxs))
        }
    }
    if (adding) {
        AddToPlaylistSheet(
            target = AddTarget.Songs(selection.idsIn(songs)),
            onDismiss = { adding = false },
            onAdded = selection::clear,
        )
    }
}
