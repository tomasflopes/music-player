package dev.tomasflopes.musicplayer.core.model.queue

/**
 * What a queue refills from (ADR 0078): a saved mix, or one song chosen from Search. Every non-manual queue entry of a
 * seeded queue carries it, so a restored queue keeps extending.
 */
sealed interface RefillSeed {
    data class Mix(
        val id: Long,
    ) : RefillSeed

    data class Song(
        val id: Long,
    ) : RefillSeed
}
