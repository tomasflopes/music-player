package dev.tomasflopes.musicplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** [MiniPlayer] wired to the playback controller; tapping the bar calls [onOpen]. */
@Composable
fun MiniPlayerHost(
    onOpen: () -> Unit,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (visible) {
        MiniPlayer(state, onOpen, viewModel::previous, viewModel::togglePlayPause, viewModel::next)
    }
}
