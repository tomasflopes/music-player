package dev.tomasflopes.musicplayer.ui.library.songs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.SongMenuButton
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.library.IndexedLazyList

@Composable
fun SongsTab(viewModel: SongsViewModel = hiltViewModel()) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val list = songs
    when {
        list == null -> Unit
        list.items.isEmpty() -> EmptyState("No songs yet. They appear here once the scan finds MP3s in your folder.")
        else ->
            IndexedLazyList(list, key = { it.id.value }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.onSongClick(index) },
                    trailing = {
                        SongMenuButton(
                            onPlayNext = { viewModel.onPlayNext(song) },
                            onAddToQueue = { viewModel.onAddToQueue(song) },
                        )
                    },
                )
            }
    }
}
