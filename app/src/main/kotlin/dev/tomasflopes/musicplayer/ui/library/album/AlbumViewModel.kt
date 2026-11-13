package dev.tomasflopes.musicplayer.ui.library.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.disc
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The album page (design 8.2): the tracks in album order with their numbers, a disc header only when the album has
 * more than one disc, and Play / Shuffle. Everything plays as [PlaySource.ALBUM]; Play and a tapped track keep the
 * tagged order, only Shuffle shuffles. The album is chosen by [open], called by the screen with its route argument.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        private val songReader: SongReader,
        private val albumReader: AlbumReader,
    ) : ViewModel() {
        private val albumId = MutableStateFlow<AlbumId?>(null)

        val state: StateFlow<AlbumUiState> =
            albumId
                .flatMapLatest { id -> if (id == null) flowOf(AlbumUiState.Loading) else render(id) }
                .stateIn(viewModelScope, WhileSubscribed, AlbumUiState.Loading)

        fun open(id: AlbumId) {
            albumId.value = id
        }

        fun onPlay() = start(startIndex = 0, shuffle = false, picked = false)

        fun onShuffle() = start(startIndex = 0, shuffle = true, picked = false)

        /** Plays the album in order, starting at the tapped track. */
        fun onTrackClick(index: Int) = start(startIndex = index, shuffle = false, picked = true)

        fun onPlayNext(song: Song) = playback.playNext(listOf(song.ref))

        fun onAddToQueue(song: Song) = playback.addToQueue(listOf(song.ref))

        private fun start(
            startIndex: Int,
            shuffle: Boolean,
            picked: Boolean,
        ) {
            val ready = state.value as? AlbumUiState.Ready ?: return
            if (startIndex !in ready.songs.indices) return
            playback.play(PlayRequest(ready.songs.map { it.ref }, startIndex, shuffle, PlaySource.ALBUM, picked))
        }

        private fun render(id: AlbumId): Flow<AlbumUiState> =
            combine(albumReader.observeAlbum(id), songReader.observeAlbumSongs(id)) { album, songs ->
                if (album == null) {
                    AlbumUiState.NotFound
                } else {
                    val cover = songs.firstOrNull { it.id == album.coverSongId }
                    AlbumUiState.Ready(album, cover, songs, entriesOf(songs))
                }
            }

        private fun entriesOf(songs: List<Song>): List<AlbumEntry> {
            val headers = songs.map { it.disc }.distinct().size > 1
            return buildList {
                var lastDisc: Int? = null
                songs.forEachIndexed { index, song ->
                    if (headers && song.disc != lastDisc) {
                        lastDisc = song.disc
                        add(AlbumEntry.DiscHeader(song.disc))
                    }
                    add(AlbumEntry.Track(index, song))
                }
            }
        }
    }
