package dev.tomasflopes.musicplayer.ui.nowplaying

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The Now Playing sheet: what the player reports, and the buttons that drive it. Shuffle and repeat keep no state
 * here; the icons follow [state], so a change made from the notification or the car shows up too (ADR 0010).
 */
@HiltViewModel
class NowPlayingViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
    ) : ViewModel() {
        val state: StateFlow<PlaybackState> = playback.state

        fun togglePlayPause() = playback.togglePlayPause()

        fun next() = playback.next()

        fun previous() = playback.previous()

        fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

        fun jumpToUpcoming(index: Int) = playback.jumpToUpcoming(index)

        fun toggleShuffle() = playback.setShuffle(!state.value.shuffle)

        fun cycleRepeatMode() = playback.cycleRepeatMode()
    }
