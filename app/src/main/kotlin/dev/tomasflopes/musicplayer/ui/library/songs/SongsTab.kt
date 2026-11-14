package dev.tomasflopes.musicplayer.ui.library.songs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.SongMenuButton
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.library.IndexedLazyList
import dev.tomasflopes.musicplayer.ui.selection.SelectionBar
import dev.tomasflopes.musicplayer.ui.selection.SelectionState

@Composable
fun SongsTab(viewModel: SongsViewModel = hiltViewModel()) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val list = songs
    val selection = remember { SelectionState() }
    Column(Modifier.fillMaxSize()) {
        SelectionBar(selection, list?.items.orEmpty())
        when {
            list == null -> Unit
            list.items.isEmpty() ->
                EmptyState(
                    "No songs yet. They appear here once the scan finds MP3s in your folder.",
                )
            else ->
                IndexedLazyList(list, key = { it.id.value }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { selection.onTap(song.id) { viewModel.onSongClick(index) } },
                        onLongClick = { selection.onLongPress(song.id) },
                        selected = selection.isSelected(song.id),
                        trailing = {
                            SongMenuButton(
                                songId = song.id,
                                onPlayNext = { viewModel.onPlayNext(song) },
                                onAddToQueue = { viewModel.onAddToQueue(song) },
                            )
                        },
                    )
                }
        }
    }
}
