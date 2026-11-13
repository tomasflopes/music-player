package dev.tomasflopes.musicplayer.ui.library.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import dev.tomasflopes.musicplayer.ui.library.IndexedList
import dev.tomasflopes.musicplayer.ui.library.indexed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An album with the song whose embedded art stands in for its cover, or `null` when none has one. */
data class AlbumListItem(
    val album: Album,
    val cover: Song?,
)

/** The Albums sub-tab of Library: albums by title, with the A-Z sections computed once per snapshot. */
@HiltViewModel
class AlbumsViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        private val songReader: SongReader,
        albumReader: AlbumReader,
    ) : ViewModel() {
        /** `null` until the first read, so an empty library and a loading one look different. */
        val albums: StateFlow<IndexedList<AlbumListItem>?> =
            albumReader
                .observeAlbums()
                .map { withCovers(it) }
                .indexed { it.album.title }
                .stateIn(viewModelScope, WhileSubscribed, null)

        /**
         * There is no album page yet (plan Task 26), so a tap plays the album in order from its first track, as a tap
         * on an album in Search does (ADR 0017). Task 26 replaces it with navigation.
         */
        fun onAlbumClick(item: AlbumListItem) {
            viewModelScope.launch {
                val songs = songReader.observeAlbumSongs(item.album.id).firstOrNull().orEmpty()
                if (songs.isEmpty()) return@launch
                playback.play(PlayRequest(songs.map { it.ref }, 0, shuffle = false, PlaySource.ALBUM, picked = false))
            }
        }

        /** One query for every cover, per snapshot, instead of one per row while scrolling. */
        private suspend fun withCovers(albums: List<Album>): List<AlbumListItem> {
            val covers = songReader.songsByIds(albums.mapNotNull { it.coverSongId }).associateBy { it.id }
            return albums.map { album -> AlbumListItem(album, album.coverSongId?.let(covers::get)) }
        }
    }
