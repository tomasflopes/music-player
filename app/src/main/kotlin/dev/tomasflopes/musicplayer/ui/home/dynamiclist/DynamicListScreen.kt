package dev.tomasflopes.musicplayer.ui.home.dynamiclist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.components.BackTitleRow
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.PlayShuffleButtons
import dev.tomasflopes.musicplayer.ui.components.SongRow
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
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val window = ready.window

    if (window != null) {
        AssistChip(
            onClick = { pickerOpen = true },
            label = { Text(daysLabel(window.days)) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Change the window") },
            modifier = Modifier.padding(horizontal = Spacing.s),
        )
    }
    PlayShuffleButtons(
        onPlay = viewModel::onPlay,
        onShuffle = viewModel::onShuffle,
        enabled = ready.songs.isNotEmpty(),
    )
    if (ready.songs.isEmpty()) {
        EmptyState(emptyText(window))
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(ready.songs, key = { _, song -> song.id.value }) { index, song ->
                SongRow(song, onClick = { viewModel.onSongClick(index) })
            }
        }
    }

    if (window != null && pickerOpen) {
        DaysWindowSheet(
            window = window,
            onQuickOption = viewModel::onQuickDays,
            onCustom = viewModel::onCustomDays,
            onDismiss = {
                pickerOpen = false
                viewModel.onPickerDismissed()
            },
        )
    }
}

private fun emptyText(window: DaysWindow?): String =
    if (window == null) "This list is empty." else "No songs added in the ${daysLabel(window.days).lowercase()}."
