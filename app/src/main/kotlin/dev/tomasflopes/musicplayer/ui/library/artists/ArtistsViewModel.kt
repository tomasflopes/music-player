package dev.tomasflopes.musicplayer.ui.library.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import dev.tomasflopes.musicplayer.ui.library.IndexedList
import dev.tomasflopes.musicplayer.ui.library.indexed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Artists sub-tab of Library: main artists by name, with the A-Z sections computed once per snapshot. */
@HiltViewModel
class ArtistsViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        private val songReader: SongReader,
        artistReader: ArtistReader,
    ) : ViewModel() {
        /** `null` until the first read, so an empty library and a loading one look different. */
        val artists: StateFlow<IndexedList<Artist>?> =
            artistReader
                .observeArtists()
                .indexed { it.name }
                .stateIn(viewModelScope, WhileSubscribed, null)

        /**
         * There is no artist page yet (plan Task 26), so a tap does what a tap on an artist in Search does (ADR 0017):
         * shuffle that artist's own songs, the way the owner plays an artist. Task 26 replaces it with navigation.
         */
        fun onArtistClick(artist: Artist) {
            viewModelScope.launch {
                val songs = songReader.observeArtistSongs(artist.id).firstOrNull().orEmpty()
                if (songs.isEmpty()) return@launch
                val refs = songs.map { it.ref }
                playback.play(PlayRequest(refs, 0, shuffle = true, PlaySource.ARTIST_SHUFFLE, picked = false))
            }
        }
    }
