package dev.tomasflopes.musicplayer.core.model.mixes

import kotlinx.coroutines.flow.Flow

/** Reads mixes. Seeds whose song is no longer in the library are left out (ADR 0021, 0037, 0076). */
interface MixReader {
    /** Oldest first, so a new mix goes to the end of the row. */
    fun observeMixes(): Flow<List<Mix>>

    /** `null` once the mix is deleted. */
    fun observeMix(id: MixId): Flow<Mix?>

    suspend fun mix(id: MixId): Mix?
}

/** Changes mixes. Times are passed in so the implementation holds no clock. */
interface MixStore {
    /** The mix and its [seeds] (in order) go in together or not at all. */
    suspend fun create(
        name: String,
        seeds: List<MixSeed>,
        nowEpochMs: Long,
    ): MixId

    suspend fun rename(
        id: MixId,
        name: String,
        nowEpochMs: Long,
    )

    /**
     * Replaces the seeds the owner can see with [seeds], in one transaction. Rows of seeds that are hidden (their song
     * left the library) are kept.
     */
    suspend fun replaceSeeds(
        id: MixId,
        seeds: List<MixSeed>,
        nowEpochMs: Long,
    )

    /** Its seeds go with it. The listening log keeps the id. */
    suspend fun delete(id: MixId)
}
