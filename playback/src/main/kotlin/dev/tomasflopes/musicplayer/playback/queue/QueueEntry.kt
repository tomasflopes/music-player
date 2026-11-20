package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed

/**
 * One slot of the queue. [manual] entries came from Play next / Add to queue and never move or reshuffle.
 * [playlistId] is the playlist the entry was played from (ADR 0042) and [mixId] the mix (ADR 0076); manual entries
 * have neither. [soloSeed] is the song a solo queue extends from (ADR 0078); it and [mixId] are never both set.
 */
data class QueueEntry(
    val ref: SongRef,
    val source: PlaySource,
    val manual: Boolean,
    val playlistId: Long? = null,
    val mixId: Long? = null,
    val soloSeed: Long? = null,
) {
    /** What a refill of the queue builds from, or null for an entry that has no seed (manual ones, plain lists). */
    val refillSeed: RefillSeed? get() = mixId?.let(RefillSeed::Mix) ?: soloSeed?.let(RefillSeed::Song)
}

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
