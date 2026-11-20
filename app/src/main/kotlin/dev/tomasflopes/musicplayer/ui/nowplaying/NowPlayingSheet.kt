package dev.tomasflopes.musicplayer.ui.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.playback.PlaybackPosition
import dev.tomasflopes.musicplayer.playback.PlaybackState
import dev.tomasflopes.musicplayer.radio.RadioViewModel
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.SectionHeader
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.components.SwipeToRemove
import dev.tomasflopes.musicplayer.ui.components.UndoSnackbarHost
import dev.tomasflopes.musicplayer.ui.playlists.SaveAsPlaylistButton
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow

/** Now Playing (design 8.2): a bottom sheet over whichever tab is showing, opened from the mini-player. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    onDismiss: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
    radio: RadioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Box {
            NowPlayingContent(
                state = state,
                position = viewModel.position,
                actions =
                    NowPlayingActions(
                        onPlayPause = viewModel::togglePlayPause,
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onSeek = viewModel::seekTo,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeat = viewModel::cycleRepeatMode,
                        onJumpToUpcoming = viewModel::jumpToUpcoming,
                        onJumpTo = viewModel::jumpTo,
                        onRemove = viewModel::remove,
                        onStartRadio = radio::start,
                    ),
            )
            // The sheet covers the shell's snackbar, so it carries its own.
            UndoSnackbarHost(Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Everything the sheet can ask the player for, so the content takes one parameter instead of eight callbacks. */
internal data class NowPlayingActions(
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onJumpToUpcoming: (Int) -> Unit,
    val onJumpTo: (Int, Song) -> Unit,
    val onRemove: (Int, Song) -> Unit,
    val onStartRadio: (SongId) -> Unit,
)

@Composable
private fun NowPlayingContent(
    state: PlaybackState,
    position: Flow<PlaybackPosition>,
    actions: NowPlayingActions,
) {
    val song = state.current
    if (song == null) {
        EmptyState("Nothing is playing.")
        return
    }
    QueueList(state, position, song, actions)
}

/** Played → now playing → up next (design 8.2); the list opens on the header, the played songs above it. */
@Composable
private fun QueueList(
    state: PlaybackState,
    position: Flow<PlaybackPosition>,
    song: Song,
    actions: NowPlayingActions,
) {
    val headerIndex = QueueLayout(state.played.size).headerIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = headerIndex)
    val headerScroll = remember { HeaderScroll() }
    LaunchedEffect(song.id) {
        if (headerScroll.shouldScrollTo(song.id)) listState.animateScrollToItem(headerIndex)
    }
    val keepScroll = remember { KeepScrollInList() }
    LazyColumn(
        Modifier.fillMaxWidth().nestedScroll(keepScroll),
        state = listState,
        contentPadding = PaddingValues(bottom = Spacing.l),
    ) {
        playedRows(state, actions)
        item(key = "now-playing") { NowPlayingHeader(state, position, song, actions) }
        item(key = "save") { SaveAsPlaylistButton("Queue", enabled = true) { saveQueue(it) } }
        upNextRows(state, actions)
    }
}

// Keyed by place in the queue and song: a song can be queued twice, and a removal moves the rows after it.
private fun LazyListScope.playedRows(
    state: PlaybackState,
    actions: NowPlayingActions,
) {
    if (state.played.isEmpty()) return
    item(key = "played-header") { SectionHeader("Played") }
    itemsIndexed(
        state.played,
        key = { i, played -> "played-${state.playedIndices[i]}-${played.id.value}" },
    ) { i, played ->
        val index = state.playedIndices[i]
        SwipeToRemove(onRemove = { actions.onRemove(index, played) }, resetKey = index) {
            SongRow(
                song = played,
                onClick = { actions.onJumpTo(index, played) },
                modifier = Modifier.alpha(PLAYED_ALPHA),
            )
        }
    }
}

private fun LazyListScope.upNextRows(
    state: PlaybackState,
    actions: NowPlayingActions,
) {
    item(key = "up-next") { SectionHeader("Up next") }
    if (state.upcoming.isEmpty()) {
        item(key = "nothing-up-next") { NothingUpNext() }
    }
    itemsIndexed(state.upcoming, key = { i, up -> "up-${upcomingKey(state, i)}-${up.id.value}" }) { i, upcoming ->
        val index = state.upcomingIndices.getOrElse(i) { -1 }
        val row: @Composable () -> Unit = {
            SongRow(
                song = upcoming,
                onClick = { if (index >= 0) actions.onJumpTo(index, upcoming) else actions.onJumpToUpcoming(i) },
                trailing = if (state.isManualUpcoming(i)) ({ QueuedMark() }) else null,
            )
        }
        // A row that repeat ALL wrapped round has no place of its own, so it cannot be removed.
        if (index >= 0) {
            SwipeToRemove(
                onRemove = { actions.onRemove(index, upcoming) },
                resetKey = index,
                content = row,
            )
        } else {
            row()
        }
    }
}

private fun upcomingKey(
    state: PlaybackState,
    i: Int,
): String = state.upcomingIndices.getOrElse(i) { -1 }.let { if (it >= 0) "$it" else "wrapped$i" }

/** Played songs are dimmed so they are easy to tell from the ones still to come. */
private const val PLAYED_ALPHA = 0.5f

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
