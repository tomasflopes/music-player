package dev.tomasflopes.musicplayer.ui.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** The transport controls shown on every tab. Now Playing will have its own. */
@HiltViewModel
class MiniPlayerViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
    ) : ViewModel() {
        val state: StateFlow<PlaybackState> = playback.state

        fun togglePlayPause() = playback.togglePlayPause()

        fun next() = playback.next()

        fun previous() = playback.previous()
    }
