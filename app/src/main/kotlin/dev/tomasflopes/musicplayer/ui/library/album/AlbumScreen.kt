package dev.tomasflopes.musicplayer.ui.library.album

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.components.AlbumHeader
import dev.tomasflopes.musicplayer.ui.components.BackTitleRow
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.PlayShuffleButtons
import dev.tomasflopes.musicplayer.ui.components.SectionHeader
import dev.tomasflopes.musicplayer.ui.components.SongMenuButton
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.theme.ArtSize

/** One album: [albumId] is the route argument; [onBack] pops it. */
@Composable
fun AlbumScreen(
    albumId: AlbumId,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    LaunchedEffect(albumId) { viewModel.open(albumId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        BackTitleRow(onBack)
        when (val shown = state) {
            AlbumUiState.Loading -> Unit
            AlbumUiState.NotFound -> EmptyState("This album does not exist.")
            is AlbumUiState.Ready -> ReadyContent(shown, viewModel)
        }
    }
}

@Composable
private fun ReadyContent(
    ready: AlbumUiState.Ready,
    viewModel: AlbumViewModel,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") { AlbumHeader(ready.album, ready.cover) }
        item(key = "buttons") {
            PlayShuffleButtons(
                onPlay = viewModel::onPlay,
                onShuffle = viewModel::onShuffle,
                enabled = ready.songs.isNotEmpty(),
            )
        }
        items(ready.entries, key = { it.key }, contentType = { it::class }) { entry ->
            when (entry) {
                is AlbumEntry.DiscHeader -> SectionHeader("Disc ${entry.disc}")
                is AlbumEntry.Track -> TrackRow(entry.song, viewModel) { viewModel.onTrackClick(entry.index) }
            }
        }
    }
}

@Composable
private fun TrackRow(
    song: Song,
    viewModel: AlbumViewModel,
    onClick: () -> Unit,
) {
    SongRow(
        song = song,
        onClick = onClick,
        trailing = {
            SongMenuButton(onPlayNext = { viewModel.onPlayNext(song) }, onAddToQueue = { viewModel.onAddToQueue(song) })
        },
        leading = { TrackNumber(song.trackNumber) },
        subtitle = song.artistName,
    )
}

/** The tagged track number in the slot a cover takes elsewhere; blank when the file has none. */
@Composable
private fun TrackNumber(number: Int?) {
    Box(Modifier.width(ArtSize.row), contentAlignment = Alignment.Center) {
        Text(
            text = number?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
