package dev.tomasflopes.musicplayer.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.ScreenTitle
import dev.tomasflopes.musicplayer.ui.components.SongRow

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    LibraryContent(songs, viewModel::playFrom)
}

@Composable
private fun LibraryContent(
    songs: List<Song>?,
    onPlay: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Library")
        when {
            songs == null -> Unit
            songs.isEmpty() -> EmptyState("No songs yet. They appear here once the scan finds MP3s in your folder.")
            else ->
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(songs, key = { _, song -> song.id.value }) { index, song ->
                        SongRow(song, onClick = { onPlay(index) })
                    }
                }
        }
    }
}
