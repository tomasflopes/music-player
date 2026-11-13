package dev.tomasflopes.musicplayer.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.ports.ScanPhase
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import dev.tomasflopes.musicplayer.ui.player.MiniPlayer

@Composable
fun LibraryScreen(
    onPickFolder: () -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val root by viewModel.musicRoot.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = { MiniPlayer(player, viewModel::previous, viewModel::togglePlayPause, viewModel::next) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (root == null) {
                FolderPrompt(onPickFolder)
            } else {
                LibraryHeader(songs.size, scan, viewModel::rescan, viewModel::shuffleAll)
                SongList(songs, viewModel::playFrom)
            }
        }
    }
}

@Composable
private fun FolderPrompt(onPickFolder: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pick your music folder", style = MaterialTheme.typography.titleLarge)
            Button(onPickFolder) { Text("Choose folder") }
        }
    }
}

@Composable
private fun LibraryHeader(
    songCount: Int,
    scan: ScanProgress,
    onRescan: () -> Unit,
    onShuffle: () -> Unit,
) {
    val label = scanLabel(scan)
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Songs", style = MaterialTheme.typography.headlineSmall)
            Text(label ?: "$songCount songs", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onRescan, enabled = label == null) { Text("Rescan") }
        Button(onShuffle, enabled = songCount > 0) { Text("Shuffle") }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    onPlay: (Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(songs, key = { _, song -> song.id.value }) { index, song ->
            SongRow(song) { onPlay(index) }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            song.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            song.artistName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

/** One scan runs through several phases, each counting from 0; naming the phase stops that looking like rescans. */
private fun scanLabel(scan: ScanProgress): String? =
    when (scan.phase) {
        ScanPhase.LISTING -> "Looking for files…"
        ScanPhase.READING_TAGS -> "Reading tags ${scan.done}/${scan.total}"
        ScanPhase.INDEXING -> "Saving ${scan.done}/${scan.total}"
        ScanPhase.FAILED -> "Scan failed"
        ScanPhase.IDLE, ScanPhase.DONE -> null
    }
