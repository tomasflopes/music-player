package dev.tomasflopes.musicplayer.ui.search

import androidx.compose.runtime.Composable
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.components.SongMenuButton
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.selection.SelectionState

/** A song result: a tap plays it (and is logged) unless a selection is going on; a long press starts one. */
@Composable
internal fun SelectableSongRow(
    song: Song,
    selection: SelectionState,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlay: () -> Unit,
) {
    SongRow(
        song = song,
        onClick = { selection.onTap(song.id, onPlay) },
        onLongClick = { selection.onLongPress(song.id) },
        selected = selection.isSelected(song.id),
        trailing = {
            SongMenuButton(
                songId = song.id,
                onPlayNext = { onPlayNext(song) },
                onAddToQueue = { onAddToQueue(song) },
            )
        },
    )
}

/** The songs on screen, which a selection can tick. */
internal fun SearchUiState.shownSongs(): List<Song> =
    when (this) {
        is SearchUiState.Results -> sections.songs.map { it.song }
        is SearchUiState.SeeAll -> items.filterIsInstance<SongItem>().map { it.song }
        else -> emptyList()
    }
