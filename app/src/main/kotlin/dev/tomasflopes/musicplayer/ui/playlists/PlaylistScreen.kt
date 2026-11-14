package dev.tomasflopes.musicplayer.ui.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.ui.components.BackTitleRow
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.PlayShuffleButtons
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * One playlist: [playlistId] is the route argument; [onBack] pops it; [onAddSongs] opens the song picker;
 * [onDeleted] leaves the page once the playlist is deleted (ADR 0043).
 */
@Composable
fun PlaylistScreen(
    playlistId: PlaylistId,
    onBack: () -> Unit,
    onAddSongs: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistId) { viewModel.open(playlistId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        val ready = state as? PlaylistUiState.Ready
        BackTitleRow(onBack, title = ready?.name.orEmpty()) {
            if (ready != null) PlaylistMenu(ready.name, viewModel, onDeleted)
        }
        when (val shown = state) {
            PlaylistUiState.Loading -> Unit
            PlaylistUiState.NotFound -> EmptyState("This playlist does not exist.")
            is PlaylistUiState.Ready -> ReadyContent(shown, viewModel, onAddSongs)
        }
    }
}

private enum class Dialog { NONE, RENAME, DELETE }

@Composable
private fun PlaylistMenu(
    name: String,
    viewModel: PlaylistViewModel,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    val scope = rememberCoroutineScope()
    Box(modifier) {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Playlist menu") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                open = false
                dialog = Dialog.RENAME
            })
            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                open = false
                dialog = Dialog.DELETE
            })
        }
    }
    when (dialog) {
        Dialog.NONE -> Unit
        Dialog.RENAME ->
            PlaylistNameDialog(
                title = "Rename playlist",
                confirmLabel = "Rename",
                initial = name,
                onConfirm = { scope.launch { if (viewModel.rename(it)) dialog = Dialog.NONE } },
                onDismiss = { dialog = Dialog.NONE },
            )
        Dialog.DELETE ->
            AlertDialog(
                onDismissRequest = { dialog = Dialog.NONE },
                title = { Text("Delete \"$name\"?") },
                text = { Text("Its songs stay in your library.") },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = Dialog.NONE
                        viewModel.delete()
                        onDeleted()
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text("Cancel") } },
            )
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
        SongList(ready.songs, viewModel)
    }
}

/** The list drags on a local copy and commits one move on drop; a new list from the database replaces the copy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongList(
    songs: List<Song>,
    viewModel: PlaylistViewModel,
) {
    var shown by remember(songs) { mutableStateOf(songs) }
    var dragged by remember { mutableStateOf<SongId?>(null) }
    val listState = rememberLazyListState()
    val reorder =
        rememberReorderableLazyListState(listState) { from, to ->
            val list = shown.toMutableList()
            val a = list.indexOfFirst { it.id.value == from.key }
            val b = list.indexOfFirst { it.id.value == to.key }
            if (a >= 0 && b >= 0) {
                list.add(b, list.removeAt(a))
                shown = list
            }
        }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(shown.size, key = { shown[it].id.value }) { index ->
            val song = shown[index]
            ReorderableItem(reorder, key = song.id.value) {
                val swipe =
                    rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it != SwipeToDismissBoxValue.Settled) viewModel.remove(song.id)
                            it != SwipeToDismissBoxValue.Settled
                        },
                    )
                SwipeToDismissBox(state = swipe, backgroundContent = {}) {
                    SongRow(
                        song,
                        onClick = { viewModel.onSongClick(songs.indexOfFirst { it.id == song.id }) },
                        trailing = {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Drag to reorder",
                                modifier =
                                    Modifier.draggableHandle(
                                        onDragStarted = { dragged = song.id },
                                        onDragStopped = {
                                            val id = dragged
                                            val from = songs.indexOfFirst { it.id == id }
                                            val to = shown.indexOfFirst { it.id == id }
                                            if (from >= 0 && to >= 0 && from != to) viewModel.move(from, to)
                                            dragged = null
                                        },
                                    ),
                            )
                        },
                    )
                }
            }
        }
    }
}
