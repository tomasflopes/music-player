package dev.tomasflopes.musicplayer.core.taste.shuffle

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.core.taste.config.SmartShuffleConfig
import kotlin.random.Random

/**
 * The last step of a mix or artist radio batch (ADR 0086): puts the finished batch in play order. The songs are
 * already chosen; an ordering only moves them, never adds or drops one. Production shuffles ([smart]); tests that pin
 * the order of the selection use [Identity].
 */
fun interface BatchOrdering {
    /**
     * @param batch the finished batch, in the order the builder made it.
     * @param preceding main artists of the songs that play right before the batch, oldest first (empty for a start).
     */
    fun order(
        batch: List<Song>,
        preceding: List<ArtistId>,
        random: Random,
    ): List<Song>

    companion object {
        /** Keeps the builder's order. */
        val Identity = BatchOrdering { batch, _, _ -> batch }

        /** Shuffles with [SmartShuffle]: no main artist twice within its gap, also against the preceding songs. */
        fun smart(config: SmartShuffleConfig = SmartShuffleConfig()): BatchOrdering =
            ShuffledBatchOrdering(SmartShuffle(config))
    }
}

/** A [BatchOrdering] that delegates to a [ShuffleStrategy] ([ShuffleStrategy.orderAfter] with the preceding songs). */
class ShuffledBatchOrdering(
    private val strategy: ShuffleStrategy,
) : BatchOrdering {
    override fun order(
        batch: List<Song>,
        preceding: List<ArtistId>,
        random: Random,
    ): List<Song> {
        // A shuffle strategy only reads the main artist of the songs before the batch, so they need no real song id.
        val before = preceding.map { SongRef(NO_SONG, it) }
        val byId = batch.associateBy { it.id }
        return strategy.orderAfter(before, batch.map { it.ref }, random).map { byId.getValue(it.songId) }
    }

    private companion object {
        val NO_SONG = SongId(-1)
    }
}
