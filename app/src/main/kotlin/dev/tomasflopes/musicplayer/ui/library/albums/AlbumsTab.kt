package dev.tomasflopes.musicplayer.ui.library.albums

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.components.AlbumRow
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.library.IndexedLazyList

@Composable
fun AlbumsTab(viewModel: AlbumsViewModel = hiltViewModel()) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val list = albums
    when {
        list == null -> Unit
        list.items.isEmpty() -> EmptyState("No albums yet. They appear here once the scan finds tagged MP3s.")
        else ->
            IndexedLazyList(list, key = { it.album.id.value }) { _, item ->
                AlbumRow(item.album, item.cover, onClick = { viewModel.onAlbumClick(item) })
            }
    }
}
