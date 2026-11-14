package dev.tomasflopes.musicplayer.ui.nowplaying

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.undo.UndoSlot
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
        private val undo: UndoSlot,
    ) : ViewModel() {
        val state: StateFlow<PlaybackState> = playback.state

        fun togglePlayPause() = playback.togglePlayPause()

        fun next() = playback.next()

        fun previous() = playback.previous()

        fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

        fun jumpToUpcoming(index: Int) = playback.jumpToUpcoming(index)

        /** Jumps to a played or upcoming song; the queue stays as it is (ADR 0047). */
        fun jumpTo(
            index: Int,
            song: Song,
        ) = playback.jumpToQueueIndex(index, song.id)

        /** Takes the song out of the queue and offers the shared undo, which puts it back at its place (ADR 0046). */
        fun remove(
            index: Int,
            song: Song,
        ) {
            val removed = playback.removeFromQueue(index, song.id) ?: return
            undo.offer("Removed \"${song.title}\" from the queue") { playback.restoreToQueue(removed) }
        }

        fun toggleShuffle() = playback.setShuffle(!state.value.shuffle)

        fun cycleRepeatMode() = playback.cycleRepeatMode()
    }
