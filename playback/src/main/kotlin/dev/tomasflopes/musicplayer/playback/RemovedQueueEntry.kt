package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource

/**
 * What a queue removal took out, with everything needed to put it back (ADR 0047): its place in the whole queue,
 * how it was queued, and, when it was the current song, where it was playing and whether it was playing.
 */
data class RemovedQueueEntry(
    val index: Int,
    val ref: SongRef,
    val source: PlaySource,
    val manual: Boolean,
    val playlistId: Long?,
    val wasCurrent: Boolean,
    val positionMs: Long,
    val wasPlaying: Boolean,
)
