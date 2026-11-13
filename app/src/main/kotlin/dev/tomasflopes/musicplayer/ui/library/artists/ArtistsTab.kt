package dev.tomasflopes.musicplayer.ui.library.artists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.components.ArtistRow
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.library.IndexedLazyList

@Composable
fun ArtistsTab(viewModel: ArtistsViewModel = hiltViewModel()) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val list = artists
    when {
        list == null -> Unit
        list.items.isEmpty() -> EmptyState("No artists yet. They appear here once the scan finds MP3s in your folder.")
        else ->
            IndexedLazyList(list, key = { it.id.value }) { _, artist ->
                ArtistRow(artist, onClick = { viewModel.onArtistClick(artist) })
            }
    }
}
