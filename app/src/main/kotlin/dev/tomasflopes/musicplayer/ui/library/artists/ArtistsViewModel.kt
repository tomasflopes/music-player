package dev.tomasflopes.musicplayer.ui.library.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import dev.tomasflopes.musicplayer.ui.library.IndexedList
import dev.tomasflopes.musicplayer.ui.library.indexed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Artists sub-tab of Library: main artists by name, with the A-Z sections computed once per snapshot. */
@HiltViewModel
class ArtistsViewModel
    @Inject
    constructor(
        artistReader: ArtistReader,
    ) : ViewModel() {
        /** `null` until the first read, so an empty library and a loading one look different. */
        val artists: StateFlow<IndexedList<Artist>?> =
            artistReader
                .observeArtists()
                .indexed { it.name }
                .stateIn(viewModelScope, WhileSubscribed, null)
    }
