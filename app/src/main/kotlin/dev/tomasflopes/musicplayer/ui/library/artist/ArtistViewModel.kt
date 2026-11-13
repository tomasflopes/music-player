package dev.tomasflopes.musicplayer.ui.library.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import dev.tomasflopes.musicplayer.ui.library.albums.AlbumListItem
import dev.tomasflopes.musicplayer.ui.library.albums.withCovers
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
 * The artist page (design 8.2, 7.11): albums, the artist's own songs, then "Featured on".
 *
 * A tap on one of the own songs, and Play, queue the own songs in their listed order ([PlaySource.ARTIST]); Shuffle
 * queues the same songs shuffled ([PlaySource.ARTIST_SHUFFLE]). Featured-on songs never join that queue: a tap on one
 * queues the Featured on list from it, as [PlaySource.ARTIST] too, because it is still a tap on this artist's page.
 * The artist is chosen by [open], called by the screen with its route argument.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        private val songReader: SongReader,
        private val artistReader: ArtistReader,
        private val albumReader: AlbumReader,
    ) : ViewModel() {
        private val artistId = MutableStateFlow<ArtistId?>(null)

        val state: StateFlow<ArtistUiState> =
            artistId
                .flatMapLatest { id -> if (id == null) flowOf(ArtistUiState.Loading) else render(id) }
                .stateIn(viewModelScope, WhileSubscribed, ArtistUiState.Loading)

        fun open(id: ArtistId) {
            artistId.value = id
        }

        fun onPlay() = playOwn(startIndex = 0, shuffle = false, picked = false)

        fun onShuffle() = playOwn(startIndex = 0, shuffle = true, picked = false)

        /** Plays the artist's own songs in their listed order, starting at the tapped one. */
        fun onSongClick(index: Int) = playOwn(startIndex = index, shuffle = false, picked = true)

        /** Plays the Featured on list in its listed order, starting at the tapped song. */
        fun onFeaturedClick(index: Int) {
            val ready = state.value as? ArtistUiState.Ready ?: return
            send(ready.featuredOn, index, shuffle = false, PlaySource.ARTIST, picked = true)
        }

        fun onPlayNext(song: Song) = playback.playNext(listOf(song.ref))

        fun onAddToQueue(song: Song) = playback.addToQueue(listOf(song.ref))

        private fun playOwn(
            startIndex: Int,
            shuffle: Boolean,
            picked: Boolean,
        ) {
            val ready = state.value as? ArtistUiState.Ready ?: return
            val source = PlaySource.ARTIST.withShuffle(shuffle)
            send(ready.songs, startIndex, shuffle, source, picked)
        }

        private fun send(
            songs: List<Song>,
            startIndex: Int,
            shuffle: Boolean,
            source: PlaySource,
            picked: Boolean,
        ) {
            if (startIndex !in songs.indices) return
            playback.play(PlayRequest(songs.map { it.ref }, startIndex, shuffle, source, picked))
        }

        private fun render(id: ArtistId): Flow<ArtistUiState> =
            combine(
                artistReader.observeArtist(id),
                songReader.observeArtistSongs(id),
                albumReader.observeArtistAlbums(id),
                songReader.observeFeaturedOn(id),
            ) { artist, songs, albums, featuredOn ->
                if (artist == null) {
                    ArtistUiState.NotFound
                } else {
                    val covered = songReader.withCovers(albums)
                    ArtistUiState.Ready(artist, songs, featuredOn, entriesOf(covered, songs, featuredOn))
                }
            }

        private fun entriesOf(
            albums: List<AlbumListItem>,
            songs: List<Song>,
            featuredOn: List<Song>,
        ): List<ArtistEntry> =
            buildList {
                if (albums.isNotEmpty()) {
                    add(ArtistEntry.Header(ArtistSection.ALBUMS))
                    albums.forEach { add(ArtistEntry.Album(it)) }
                }
                if (songs.isNotEmpty()) {
                    add(ArtistEntry.Header(ArtistSection.SONGS))
                    songs.forEachIndexed { index, song -> add(ArtistEntry.Track(index, song)) }
                }
                if (featuredOn.isNotEmpty()) {
                    add(ArtistEntry.Header(ArtistSection.FEATURED_ON))
                    featuredOn.forEachIndexed { index, song -> add(ArtistEntry.Featured(index, song)) }
                }
            }
    }
