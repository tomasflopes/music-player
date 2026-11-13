package dev.tomasflopes.musicplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** [MiniPlayer] wired to the playback controller. */
@Composable
fun MiniPlayerHost(viewModel: MiniPlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MiniPlayer(state, viewModel::previous, viewModel::togglePlayPause, viewModel::next)
}
