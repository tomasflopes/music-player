package dev.tomasflopes.musicplayer.ui.playlists

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.components.SectionHeader
import dev.tomasflopes.musicplayer.ui.components.SongRow

/**
 * "Suggested for this playlist" as the last rows of a playlist list (design 7.13): a header, then each song with "+"
 * (add it) and "✕" (dismiss it). Draws nothing when there is no suggestion. Keys are text so they never meet the
 * song-id keys of the rows around them while a song moves from here into the playlist.
 */
fun LazyListScope.suggestionsBlock(
    songs: List<Song>,
    onAdd: (Song) -> Unit,
    onDismiss: (Song) -> Unit,
) {
    if (songs.isEmpty()) return
    item(key = "suggestions-header") { SectionHeader("Suggested for this playlist") }
    items(songs.size, key = { "suggested-${songs[it].id.value}" }) { index ->
        val song = songs[index]
        SongRow(
            song = song,
            onClick = {},
            trailing = {
                Row {
                    IconButton(onClick = { onAdd(song) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add ${song.title} to the playlist")
                    }
                    IconButton(onClick = { onDismiss(song) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss ${song.title}")
                    }
                }
            },
        )
    }
}
