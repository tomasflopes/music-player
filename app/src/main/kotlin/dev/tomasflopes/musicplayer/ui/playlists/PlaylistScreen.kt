package dev.tomasflopes.musicplayer.ui.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.ui.components.BackTitleRow
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.PlayShuffleButtons
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** One playlist: [playlistId] is the route argument; [onBack] pops it; [onAddSongs] opens the song picker. */
@Composable
fun PlaylistScreen(
    playlistId: PlaylistId,
    onBack: () -> Unit,
    onAddSongs: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistId) { viewModel.open(playlistId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        BackTitleRow(onBack, title = (state as? PlaylistUiState.Ready)?.name.orEmpty())
        when (val shown = state) {
            PlaylistUiState.Loading -> Unit
            PlaylistUiState.NotFound -> EmptyState("This playlist does not exist.")
            is PlaylistUiState.Ready -> ReadyContent(shown, viewModel, onAddSongs)
        }
    }
}

@Composable
private fun ReadyContent(
    ready: PlaylistUiState.Ready,
    viewModel: PlaylistViewModel,
    onAddSongs: () -> Unit,
) {
    PlayShuffleButtons(
        onPlay = viewModel::onPlay,
        onShuffle = viewModel::onShuffle,
        enabled = ready.songs.isNotEmpty(),
    )
    OutlinedButton(onClick = onAddSongs, modifier = Modifier.padding(horizontal = Spacing.s)) {
        Text("Add songs")
    }
    if (ready.songs.isEmpty()) {
        EmptyState("No songs yet. Tap Add songs, or use + on any song.")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(ready.songs, key = { _, song -> song.id.value }) { index, song ->
                SongRow(song, onClick = { viewModel.onSongClick(index) })
            }
        }
    }
}
