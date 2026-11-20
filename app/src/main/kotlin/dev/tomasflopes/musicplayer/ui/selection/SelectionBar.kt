package dev.tomasflopes.musicplayer.ui.selection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
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
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.ui.playlists.AddTarget
import dev.tomasflopes.musicplayer.ui.playlists.AddToPlaylistSheet
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/**
 * Shown above a list while [selection] is active: how many are ticked, Close, and Add to playlist for the ticked
 * songs of [songs] (in list order). Back ends the selection first. Draws nothing when there is no selection.
 * A screen that passes [onPlayNext] and [onAddToQueue] also gets those two buttons for the ticked songs (in list
 * order); the selection ends after either (ADR 0082). The other screens are unchanged.
 */
@Composable
fun SelectionBar(
    selection: SelectionState,
    songs: List<Song>,
    modifier: Modifier = Modifier,
    onPlayNext: ((List<SongId>) -> Unit)? = null,
    onAddToQueue: ((List<SongId>) -> Unit)? = null,
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
        onPlayNext?.let { act ->
            IconButton(onClick = { selection.finish(songs, act) }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Play next")
            }
        }
        onAddToQueue?.let { act ->
            IconButton(onClick = { selection.finish(songs, act) }) {
                Icon(Icons.Default.Add, contentDescription = "Add to queue")
            }
        }
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

/** Hands the ticked songs of [songs] to [action], then ends the selection. */
private fun SelectionState.finish(
    songs: List<Song>,
    action: (List<SongId>) -> Unit,
) {
    action(idsIn(songs))
    clear()
}
