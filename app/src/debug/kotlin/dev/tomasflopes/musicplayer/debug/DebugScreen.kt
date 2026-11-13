package dev.tomasflopes.musicplayer.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DebugScreen(
    viewModel: DebugViewModel,
    onPickFolder: () -> Unit,
) {
    val state by viewModel.playbackState.collectAsState()
    val scan by viewModel.scan.collectAsState()
    val count by viewModel.songCount.collectAsState()
    val events by viewModel.recentEvents.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$count songs · scan ${scan.phase} ${scan.done}/${scan.total}",
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPickFolder) { Text("Pick folder") }
            Button(onClick = viewModel::scanNow) { Text("Scan") }
            Button(onClick = viewModel::shuffleAll) { Text("Shuffle all") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::togglePlayPause) { Text(if (state.isPlaying) "Pause" else "Play") }
            Button(onClick = viewModel::next) { Text("Next") }
            Button(onClick = viewModel::toggleShuffle) { Text("Shuffle ${if (state.shuffle) "on" else "off"}") }
            Button(onClick = viewModel::cycleRepeat) { Text("Repeat ${state.repeatMode}") }
        }
        Text(state.current?.let { "${it.artistName} – ${it.title}" } ?: "Nothing playing")
        val position = state.positionMs / MS_PER_SECOND
        val duration = state.durationMs / MS_PER_SECOND
        Text("${position}s / ${duration}s · next: ${state.upcoming.firstOrNull()?.title}")
        Text("Last plays", style = MaterialTheme.typography.titleSmall)
        LazyColumn {
            items(events) { Text("${it.songId.value} ${it.endedBy} ${it.listenedMs}ms ${it.source.wire}") }
        }
    }
}

private const val MS_PER_SECOND = 1_000
