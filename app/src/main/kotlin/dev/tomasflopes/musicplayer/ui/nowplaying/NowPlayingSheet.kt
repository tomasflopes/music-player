package dev.tomasflopes.musicplayer.ui.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

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
                        onMove = viewModel::move,
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
    /** Drags the Up next row at the first row number to the second (ADR 0081). */
    val onMove: (Int, Int) -> Unit,
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
    val upRows = remember(state.upcoming, state.upcomingIndices, state.manualUpcoming) { upRowsOf(state) }
    val drag = rememberUpNextDrag(upRows, listState, actions.onMove)
    LazyColumn(
        Modifier.fillMaxWidth().nestedScroll(keepScroll),
        state = listState,
        contentPadding = PaddingValues(bottom = Spacing.l),
    ) {
        playedRows(state, actions)
        item(key = "now-playing") { NowPlayingHeader(state, position, song, actions) }
        item(key = "save") { SaveAsPlaylistButton("Queue", enabled = true) { saveQueue(it) } }
        upNextRows(drag, actions)
    }
}

/** One Up next row: its place in the queue (-1 when repeat ALL wrapped it round) and whether it was queued by hand. */
private data class UpRow(
    val song: Song,
    val index: Int,
    val manual: Boolean,
    val row: Int,
) {
    /** A row passes over rows of its own group only, and never over one that repeat ALL wrapped round. */
    fun canPassOver(other: UpRow): Boolean = manual == other.manual && other.index >= 0

    /** Stable while a row is dragged; a song can be queued twice, and a wrapped row has no place of its own. */
    val key: String get() = "up-${if (index >= 0) "$index" else "wrapped$row"}-${song.id.value}"
}

private fun upRowsOf(state: PlaybackState) =
    state.upcoming.mapIndexed { i, song ->
        UpRow(song, state.upcomingIndices.getOrElse(i) { -1 }, state.isManualUpcoming(i), i)
    }

/**
 * The Up next list while it is dragged: a local copy that follows the finger, committed with one move on drop, as
 * the playlist page does (ADR 0043). A row cannot be dragged over the other group, so manual songs stay before auto
 * ones (ADR 0081). A new queue state from the player replaces the copy.
 */
private class UpNextDrag(
    val rows: List<UpRow>,
    val reorder: ReorderableLazyListState,
    val onDragStarted: (String) -> Unit,
    val onDragStopped: () -> Unit,
)

@Composable
private fun rememberUpNextDrag(
    upRows: List<UpRow>,
    listState: LazyListState,
    onMove: (Int, Int) -> Unit,
): UpNextDrag {
    var shown by remember(upRows) { mutableStateOf(upRows) }
    var dragged by remember { mutableStateOf<String?>(null) }
    val reorder =
        rememberReorderableLazyListState(listState) { from, to ->
            val list = shown.toMutableList()
            val a = list.indexOfFirst { it.key == from.key }
            val b = list.indexOfFirst { it.key == to.key }
            if (a >= 0 && b >= 0 && list[a].canPassOver(list[b])) {
                list.add(b, list.removeAt(a))
                shown = list
            }
        }
    return UpNextDrag(
        rows = shown,
        reorder = reorder,
        onDragStarted = { dragged = it },
        onDragStopped = {
            val key = dragged
            val from = upRows.indexOfFirst { it.key == key }
            val to = shown.indexOfFirst { it.key == key }
            if (from >= 0 && to >= 0 && from != to) onMove(from, to)
            dragged = null
        },
    )
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
    drag: UpNextDrag,
    actions: NowPlayingActions,
) {
    item(key = "up-next") { SectionHeader("Up next") }
    if (drag.rows.isEmpty()) {
        item(key = "nothing-up-next") { NothingUpNext() }
    }
    items(drag.rows, key = { it.key }) { row ->
        if (row.index >= 0) {
            // Only upcoming songs have a drag handle; played songs and the current one do not move (ADR 0081).
            ReorderableItem(drag.reorder, key = row.key) {
                SwipeToRemove(
                    onRemove = { actions.onRemove(row.index, row.song) },
                    resetKey = row.index,
                ) {
                    SongRow(
                        song = row.song,
                        onClick = { actions.onJumpTo(row.index, row.song) },
                        trailing = {
                            if (row.manual) QueuedMark()
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Drag to reorder",
                                modifier =
                                    Modifier.draggableHandle(
                                        onDragStarted = { drag.onDragStarted(row.key) },
                                        onDragStopped = { drag.onDragStopped() },
                                    ),
                            )
                        },
                    )
                }
            }
        } else {
            // A row that repeat ALL wrapped round has no place of its own, so it cannot be removed or moved.
            SongRow(
                song = row.song,
                onClick = { actions.onJumpToUpcoming(row.row) },
                trailing = if (row.manual) ({ QueuedMark() }) else null,
            )
        }
    }
}

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
