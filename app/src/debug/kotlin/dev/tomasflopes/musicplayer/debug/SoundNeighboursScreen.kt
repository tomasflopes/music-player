package dev.tomasflopes.musicplayer.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.tomasflopes.musicplayer.core.analysis.SoundVectorizer
import dev.tomasflopes.musicplayer.core.model.Song
import java.util.Locale

/** Debug → Sound neighbours: a seed from search, its 20 nearest songs with distance and five values (ADR 0073). */
@Composable
fun SoundNeighboursScreen(viewModel: SoundNeighboursViewModel) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Sound neighbours", style = MaterialTheme.typography.titleMedium)
            Text("Columns: ${SoundVectorizer.NAMES.joinToString(" ")} (normalised, weighted). Tap to play.")
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Seed song") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(state.matches) { song -> SongLine(song, "", Modifier.clickable { viewModel.onSeedPicked(song) }) }
        state.seed?.let { seed ->
            item { SongLine(seed, "seed  ${numbers(state.seedValues)}", Modifier) }
        }
        if (state.message.isNotEmpty()) item { Text(state.message) }
        itemsIndexed(state.neighbours) { index, row ->
            val detail = String.format(Locale.ROOT, "d=%.3f  %s", row.distance, numbers(row.values))
            SongLine(row.song, detail, Modifier.clickable { viewModel.onNeighbourClick(index) })
        }
    }
}

@Composable
private fun SongLine(
    song: Song,
    detail: String,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text("${song.title} - ${song.artistName}", style = MaterialTheme.typography.bodyMedium)
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun numbers(values: FloatArray): String = values.joinToString(" ") { String.format(Locale.ROOT, "%.2f", it) }
