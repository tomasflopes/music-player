package dev.tomasflopes.musicplayer.ui.library.artist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.components.AlbumRow
import dev.tomasflopes.musicplayer.ui.components.BackTitleRow
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.PlayShuffleButtons
import dev.tomasflopes.musicplayer.ui.components.SectionHeader
import dev.tomasflopes.musicplayer.ui.components.SongMenuButton
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.library.artist.photo.ArtistPhotoHeader
import dev.tomasflopes.musicplayer.ui.library.artist.photo.ChangePhotoSheet

/** One artist: [artistId] is the route argument; [onBack] pops it and [onOpenAlbum] opens one of the albums. */
@Composable
fun ArtistScreen(
    artistId: ArtistId,
    onBack: () -> Unit,
    onOpenAlbum: (AlbumId) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    LaunchedEffect(artistId) { viewModel.open(artistId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        BackTitleRow(onBack, title = (state as? ArtistUiState.Ready)?.artist?.name.orEmpty())
        when (val shown = state) {
            ArtistUiState.Loading -> Unit
            ArtistUiState.NotFound -> EmptyState("This artist does not exist.")
            is ArtistUiState.Ready -> ReadyContent(shown, viewModel, onOpenAlbum)
        }
    }
}

@Composable
private fun ReadyContent(
    ready: ArtistUiState.Ready,
    viewModel: ArtistViewModel,
    onOpenAlbum: (AlbumId) -> Unit,
) {
    var changingPhoto by rememberSaveable { mutableStateOf(false) }
    if (changingPhoto) ChangePhotoSheet(ready.artist, onDismiss = { changingPhoto = false })
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "photo") { ArtistPhotoHeader(ready.artist, onChangePhoto = { changingPhoto = true }) }
        item(key = "buttons") {
            PlayShuffleButtons(
                onPlay = viewModel::onPlay,
                onShuffle = viewModel::onShuffle,
                enabled = ready.songs.isNotEmpty(),
            )
        }
        items(ready.entries, key = { it.key }, contentType = { it::class }) { entry ->
            when (entry) {
                is ArtistEntry.Header -> SectionHeader(entry.section.title)
                is ArtistEntry.Album -> {
                    val item = entry.item
                    AlbumRow(item.album, item.cover, onClick = { onOpenAlbum(item.album.id) })
                }
                is ArtistEntry.Track -> SongEntryRow(entry.song, viewModel) { viewModel.onSongClick(entry.index) }
                is ArtistEntry.Featured ->
                    SongEntryRow(entry.song, viewModel) { viewModel.onFeaturedClick(entry.index) }
            }
        }
    }
}

@Composable
private fun SongEntryRow(
    song: Song,
    viewModel: ArtistViewModel,
    onClick: () -> Unit,
) {
    SongRow(
        song = song,
        onClick = onClick,
        trailing = {
            SongMenuButton(onPlayNext = { viewModel.onPlayNext(song) }, onAddToQueue = { viewModel.onAddToQueue(song) })
        },
    )
}
