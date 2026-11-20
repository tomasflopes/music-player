package dev.tomasflopes.musicplayer.refill

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.playback.RefillSignal

/** The songs to append and the source they are logged with (ADR 0079). */
data class RefillBatch(
    val songs: List<Song>,
    val source: PlaySource,
) {
    companion object {
        /** Nothing could be built: no profile, a deleted mix, a library with nothing left to add. */
        val NONE = RefillBatch(emptyList(), PlaySource.RADIO)
    }
}

/**
 * One kind of refill (ADR 0078): which seed it extends, how low the queue may run before it acts, and how it builds the
 * next batch. New behaviour is a new implementation in the Hilt set, not a branch in the refiller.
 */
interface RefillSource {
    fun handles(seed: RefillSeed): Boolean

    /** A refill starts when this many non-manual songs or fewer are left. */
    val lowWater: Int

    /** Read at each refill, so a setting changed mid-queue applies at the next one. */
    suspend fun enabled(): Boolean

    /** Builds the batch for [signal]; runs its own work on `Dispatchers.Default`. */
    suspend fun batch(signal: RefillSignal): RefillBatch
}
