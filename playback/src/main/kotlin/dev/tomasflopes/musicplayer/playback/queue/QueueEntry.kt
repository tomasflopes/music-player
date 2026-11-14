package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource

/**
 * One slot of the queue. [manual] entries came from Play next / Add to queue and never move or reshuffle.
 * [playlistId] is the playlist the entry was played from (ADR 0042); manual entries have none.
 */
data class QueueEntry(
    val ref: SongRef,
    val source: PlaySource,
    val manual: Boolean,
    val playlistId: Long? = null,
)

enum class RepeatMode { OFF, ALL, ONE }

/** Everything needed to rebuild a [PlayQueue] after the process dies. */
data class QueueSnapshot(
    val entries: List<QueueEntry>,
    /** The non-manual songs in the order they were handed to `replace`, for turning shuffle off. */
    val originalOrder: List<SongRef>,
    val index: Int,
    val shuffled: Boolean,
    val repeatMode: RepeatMode,
)
