package dev.tomasflopes.musicplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.playback.PlaybackState
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The transport controls shown on every tab. Tapping the bar opens Now Playing, which has its own ViewModel. */
@HiltViewModel
class MiniPlayerViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
    ) : ViewModel() {
        val state: StateFlow<PlaybackState> = playback.state

        /** The bar exists only while a song is loaded. */
        val visible: StateFlow<Boolean> =
            playback.state
                .map { it.current != null }
                .stateIn(viewModelScope, WhileSubscribed, playback.state.value.current != null)

        fun togglePlayPause() = playback.togglePlayPause()

        fun next() = playback.next()

        fun previous() = playback.previous()
    }
