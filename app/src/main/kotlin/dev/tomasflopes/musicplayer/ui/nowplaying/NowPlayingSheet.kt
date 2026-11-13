package dev.tomasflopes.musicplayer.ui.nowplaying

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.playback.PlaybackState
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.SectionHeader
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** Now Playing (design 8.2): a bottom sheet over whichever tab is showing, opened from the mini-player. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    onDismiss: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        NowPlayingContent(
            state = state,
            actions =
                NowPlayingActions(
                    onPlayPause = viewModel::togglePlayPause,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onSeek = viewModel::seekTo,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onJumpToUpcoming = viewModel::jumpToUpcoming,
                ),
        )
    }
}

/** Everything the sheet can ask the player for, so the content takes one parameter instead of seven callbacks. */
internal data class NowPlayingActions(
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onJumpToUpcoming: (Int) -> Unit,
)

@Composable
private fun NowPlayingContent(
    state: PlaybackState,
    actions: NowPlayingActions,
) {
    val song = state.current
    if (song == null) {
        EmptyState("Nothing is playing.")
        return
    }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = Spacing.l)) {
        item { NowPlayingHeader(state, song, actions) }
        item { SectionHeader("Up next") }
        if (state.upcoming.isEmpty()) {
            item { NothingUpNext() }
        }
        // Keyed by position: a song can be queued twice, and the list reorders live when shuffle is toggled.
        itemsIndexed(state.upcoming, key = { index, _ -> index }) { index, upcoming ->
            SongRow(
                song = upcoming,
                onClick = { actions.onJumpToUpcoming(index) },
                trailing = if (state.isManualUpcoming(index)) ({ QueuedMark() }) else null,
            )
        }
    }
}

@Composable
private fun NothingUpNext() {
    Text(
        text = "Nothing queued after this song.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
    )
}

/** Marks a song queued by hand (Play next / Add to queue), which shuffle never moves. */
@Composable
private fun QueuedMark() {
    Text(
        text = "Queued",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
