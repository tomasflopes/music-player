package dev.tomasflopes.musicplayer.ui.home.dynamiclist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.components.BackTitleRow
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.PlayShuffleButtons
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.selection.SelectionBar
import dev.tomasflopes.musicplayer.ui.selection.SelectionState
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** One dynamic list: [listId] is the route argument; [onBack] pops it. */
@Composable
fun DynamicListScreen(
    listId: String,
    onBack: () -> Unit,
    viewModel: DynamicListViewModel = hiltViewModel(),
) {
    LaunchedEffect(listId) { viewModel.open(listId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        BackTitleRow(onBack, title = (state as? DynamicListUiState.Ready)?.title.orEmpty())
        when (val shown = state) {
            DynamicListUiState.Loading -> Unit
            DynamicListUiState.NotFound -> EmptyState("This list does not exist.")
            is DynamicListUiState.Ready -> ReadyContent(shown, viewModel)
        }
    }
}

@Composable
private fun ReadyContent(
    ready: DynamicListUiState.Ready,
    viewModel: DynamicListViewModel,
) {
    ready.windowDays?.let { WindowCaption(daysLabel(it)) }
    PlayShuffleButtons(
        onPlay = viewModel::onPlay,
        onShuffle = viewModel::onShuffle,
        enabled = ready.songs.isNotEmpty(),
    )
    val selection = remember { SelectionState() }
    SelectionBar(selection, ready.songs)
    if (ready.songs.isEmpty()) {
        EmptyState(emptyText(ready.windowDays))
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(ready.songs, key = { _, song -> song.id.value }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { selection.onTap(song.id) { viewModel.onSongClick(index) } },
                    onLongClick = { selection.onLongPress(song.id) },
                    selected = selection.isSelected(song.id),
                )
            }
        }
    }
}

/** Which window the list shows. Read-only: the number is changed in Settings. */
@Composable
private fun WindowCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxxs),
    )
}

private fun emptyText(windowDays: Int?): String =
    if (windowDays == null) "This list is empty." else "No songs added in the ${daysLabel(windowDays).lowercase()}."
