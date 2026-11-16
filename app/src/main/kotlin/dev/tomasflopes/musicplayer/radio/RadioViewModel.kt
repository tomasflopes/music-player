package dev.tomasflopes.musicplayer.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.SongId
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the song menu and the Now playing button talk to: one call, no logic (ADR 0072). */
@HiltViewModel
class RadioViewModel
    @Inject
    constructor(
        private val startRadio: StartRadio,
    ) : ViewModel() {
        fun start(seedId: SongId) {
            viewModelScope.launch { startRadio(seedId) }
        }
    }
