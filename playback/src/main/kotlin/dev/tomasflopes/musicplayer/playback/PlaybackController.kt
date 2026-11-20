package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** What screens use to play music. Nothing outside `:playback` touches Media3. */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    /**
     * The current song's position and length (ADR 0077). Cold: each collector reads the player directly, ticks every
     * [PlaybackConfig.positionTickMs] only while the player is playing, and while paused emits once and again on a
     * seek or any other player event. Collect it on the main thread, as close to where it is drawn as possible.
     */
    val position: Flow<PlaybackPosition>

    fun play(request: PlayRequest)

    /** The queue from the current song on, as it is now. */
    fun queueContent(): QueueContent

    fun playNext(songs: List<SongRef>)

    fun addToQueue(songs: List<SongRef>)

    /**
     * Appends a refill batch built for the queue of [epoch] and [seed] (ADR 0078). The engine drops it when that queue
     * is gone, and leaves out songs already queued.
     */
    fun appendBatch(
        songs: List<SongRef>,
        seed: RefillSeed,
        epoch: Int,
        source: PlaySource,
    )

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

    /**
     * Drags the upcoming song at [from] to the place [to] of the whole queue (ADR 0081). Ignored when [songId] is not
     * at [from] any more, or when the song is the current or a played one. A manual song stops among the manual songs,
     * an auto song among the auto songs.
     */
    fun moveInQueue(
        from: Int,
        to: Int,
        songId: SongId,
    )

    /** Puts a removed entry back at its place; a removed current song becomes current again, where it was. */
    fun restoreToQueue(entry: RemovedQueueEntry)

    fun setShuffle(on: Boolean)

    /** OFF → ALL → ONE → OFF. */
    fun cycleRepeatMode()
}
