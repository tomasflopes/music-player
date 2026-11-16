package dev.tomasflopes.musicplayer.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the song menu, the Now playing button and the artist page talk to: one call, no logic (ADR 0072, 0075). */
@HiltViewModel
class RadioViewModel
    @Inject
    constructor(
        private val startRadio: StartRadio,
        private val startArtistRadio: StartArtistRadio,
    ) : ViewModel() {
        fun start(seedId: SongId) {
            viewModelScope.launch { startRadio(seedId) }
        }

        fun startArtist(artistId: ArtistId) {
            viewModelScope.launch { startArtistRadio(artistId) }
        }
    }
