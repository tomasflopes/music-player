package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.SongId
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

    /** Jumps to the entry at [index] of the whole queue, played or upcoming; the queue stays as it is. */
    fun jumpToQueueIndex(
        index: Int,
        songId: SongId,
    )

    /**
     * Takes the entry at [index] out of the queue (ADR 0047). `null` when nothing is there or it is not [songId]
     * any more, so a stale screen removes nothing. Removing the current song moves on to the next one.
     */
    fun removeFromQueue(
        index: Int,
        songId: SongId,
    ): RemovedQueueEntry?

    /** Puts a removed entry back at its place; a removed current song becomes current again, where it was. */
    fun restoreToQueue(entry: RemovedQueueEntry)

    fun setShuffle(on: Boolean)

    /** OFF → ALL → ONE → OFF. */
    fun cycleRepeatMode()
}
