package dev.tomasflopes.musicplayer.ui.playlists

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.ui.components.BackTitleRow
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.SearchField
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.home.sections.songCountLabel
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import kotlinx.coroutines.launch

/** "Add songs" to [playlistId]: search the library, tick songs, add them all at once. */
@Composable
fun SongPickerScreen(
    playlistId: PlaylistId,
    onBack: () -> Unit,
    viewModel: SongPickerViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistId) { viewModel.open(playlistId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        BackTitleRow(onBack, title = if (state.playlistName.isEmpty()) "Add songs" else "Add to ${state.playlistName}")
        SearchField(state.query, viewModel::onQueryChange, placeholder = "Search your songs")
        if (state.songs.isEmpty()) {
            EmptyState("No songs match.", Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(state.songs, key = { it.id.value }) { song ->
                    val inside = song.id in state.alreadyIn
                    SongRow(
                        song = song,
                        onClick = { if (!inside) viewModel.onToggle(song.id) },
                        selected = song.id in state.selected,
                        trailing = {
                            if (inside) {
                                Text("Added", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Checkbox(
                                    checked = song.id in state.selected,
                                    onCheckedChange = { viewModel.onToggle(song.id) },
                                )
                            }
                        },
                    )
                }
            }
        }
        if (state.selected.isNotEmpty()) {
            Button(
                onClick = {
                    scope.launch {
                        viewModel.addSelected()?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(Spacing.s),
            ) {
                Text("Add ${songCountLabel(state.selected.size)}")
            }
        }
    }
}
