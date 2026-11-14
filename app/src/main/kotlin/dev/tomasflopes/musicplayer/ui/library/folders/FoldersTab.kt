package dev.tomasflopes.musicplayer.ui.library.folders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.components.BackTitleRow
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.FolderRow
import dev.tomasflopes.musicplayer.ui.components.PlayShuffleButtons
import dev.tomasflopes.musicplayer.ui.components.SectionHeader
import dev.tomasflopes.musicplayer.ui.components.SongMenuButton
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.selection.SelectionBar
import dev.tomasflopes.musicplayer.ui.selection.SelectionState

/** The Folders sub-tab. System back goes up one level while a folder is open; at the music folder it is not taken. */
@Composable
fun FoldersTab(viewModel: FoldersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shown = state ?: return
    BackHandler(enabled = shown.canGoUp) { viewModel.onBack() }
    Column(Modifier.fillMaxSize()) {
        val current = shown.current
        if (current != null) {
            BackTitleRow(onBack = { viewModel.onBack() })
            SectionHeader(current.name)
        }
        if (shown.folders.isEmpty() && shown.songs.isEmpty()) {
            EmptyState(if (current == null) "No folders yet. They appear here once the scan finds MP3s." else "Empty.")
        } else {
            FolderContent(shown, viewModel)
        }
    }
}

@Composable
private fun FolderContent(
    shown: FoldersUiState,
    viewModel: FoldersViewModel,
) {
    val selection = remember { SelectionState() }
    Column(Modifier.fillMaxSize()) {
        SelectionBar(selection, shown.songs)
        FolderList(shown, viewModel, selection)
    }
}

@Composable
private fun FolderList(
    shown: FoldersUiState,
    viewModel: FoldersViewModel,
    selection: SelectionState,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "buttons") {
            PlayShuffleButtons(
                onPlay = viewModel::onPlay,
                onShuffle = viewModel::onShuffle,
                enabled = shown.songs.isNotEmpty(),
            )
        }
        if (shown.folders.isNotEmpty()) {
            item(key = "folders-header") { SectionHeader("Folders") }
            items(shown.folders, key = { "folder:${it.path}" }) { folder ->
                FolderRow(folder, onClick = { viewModel.onOpenFolder(folder) })
            }
        }
        if (shown.songs.isNotEmpty()) {
            item(key = "songs-header") { SectionHeader("Songs") }
            items(shown.songs.size, key = { "song:${shown.songs[it].id.value}" }) { index ->
                val song = shown.songs[index]
                SongRow(
                    song = song,
                    onClick = { selection.onTap(song.id) { viewModel.onSongClick(index) } },
                    onLongClick = { selection.onLongPress(song.id) },
                    selected = selection.isSelected(song.id),
                    trailing = {
                        SongMenuButton(
                            songId = song.id,
                            onPlayNext = { viewModel.onPlayNext(song) },
                            onAddToQueue = { viewModel.onAddToQueue(song) },
                        )
                    },
                )
            }
        }
    }
}
