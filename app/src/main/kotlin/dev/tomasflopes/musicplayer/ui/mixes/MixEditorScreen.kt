package dev.tomasflopes.musicplayer.ui.mixes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.mixes.MixLimits
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeedKind
import dev.tomasflopes.musicplayer.ui.components.ArtistRow
import dev.tomasflopes.musicplayer.ui.components.BackTitleRow
import dev.tomasflopes.musicplayer.ui.components.SearchField
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Create ([mixId] null) or edit a mix with seeds picked from songs and artists. */
@Composable
fun MixEditorScreen(
    mixId: Long?,
    onBack: () -> Unit,
    viewModel: MixEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(mixId) { viewModel.open(mixId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val picked = state.seeds.map { it.seed }.toSet()
    Column(Modifier.fillMaxSize()) {
        MixEditorContent(state, picked, viewModel, onBack, scope) { confirmingDelete = true }
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this mix?") },
            text = { Text("The mix goes away. Your listening history keeps its plays.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        scope.launch {
                            viewModel.delete()
                            onBack()
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ColumnScope.MixEditorContent(
    state: MixEditorState,
    picked: Set<MixSeed>,
    viewModel: MixEditorViewModel,
    onBack: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    onDelete: () -> Unit,
) {
    BackTitleRow(onBack, title = if (state.isNew) "New mix" else "Edit mix")
    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s),
    )
    Text(
        text =
            "Seeds: ${state.seeds.size} of ${MixLimits.MAX_SEEDS} " +
                "(pick at least ${MixLimits.CREATE_MIN_SEEDS})",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
    )
    SelectedSeeds(state, viewModel)
    SearchField(state.query, viewModel::onQueryChange, placeholder = "Search songs and artists to add")
    SearchResults(state, picked, viewModel)
    state.message?.let { message ->
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (!state.isNew) {
            TextButton(onClick = onDelete) { Text("Delete mix") }
        }
        Button(
            onClick = { scope.launch { if (viewModel.save()) onBack() } },
            modifier = Modifier.weight(1f),
        ) {
            Text(if (state.isNew) "Create mix" else "Save")
        }
    }
}

@Composable
private fun SelectedSeeds(
    state: MixEditorState,
    viewModel: MixEditorViewModel,
) {
    state.seeds.forEach { item ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(item.label, modifier = Modifier.weight(1f), maxLines = 1)
            TextButton(onClick = { viewModel.toggle(item.seed) }) { Text("Remove") }
        }
    }
}

@Composable
private fun ColumnScope.SearchResults(
    state: MixEditorState,
    picked: Set<MixSeed>,
    viewModel: MixEditorViewModel,
) {
    LazyColumn(Modifier.weight(1f)) {
        items(state.artists, key = { "artist-${it.id.value}" }) { artist ->
            ArtistRow(
                artist = artist,
                onClick = { viewModel.toggle(MixSeed(MixSeedKind.ARTIST, artist.id.value)) },
            )
        }
        items(state.songs, key = { "song-${it.id.value}" }) { song ->
            val seed = MixSeed(MixSeedKind.SONG, song.id.value)
            SongRow(
                song = song,
                onClick = { viewModel.toggle(seed) },
                selected = seed in picked,
                trailing = {
                    Checkbox(
                        checked = seed in picked,
                        onCheckedChange = { viewModel.toggle(seed) },
                    )
                },
            )
        }
    }
}
