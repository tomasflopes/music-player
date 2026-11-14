package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.SongRef
import kotlinx.coroutines.flow.StateFlow

/** What screens use to play music. Nothing outside `:playback` touches Media3. */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    fun play(request: PlayRequest)

    /** The queue from the current song on, as it is now. */
    fun queueContent(): QueueContent

    fun playNext(songs: List<SongRef>)

    fun addToQueue(songs: List<SongRef>)

    fun togglePlayPause()

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    fun jumpToUpcoming(index: Int)

    fun setShuffle(on: Boolean)

    /** OFF → ALL → ONE → OFF. */
    fun cycleRepeatMode()
}
