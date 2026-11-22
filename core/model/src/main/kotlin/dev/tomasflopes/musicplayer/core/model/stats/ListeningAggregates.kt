package dev.tomasflopes.musicplayer.core.model.stats

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent

/** A stretch of time, half-open: it includes [startEpochMs] and leaves out [endEpochMs]. */
data class TimeRange(
    val startEpochMs: Long,
    val endEpochMs: Long,
) {
    init {
        require(endEpochMs > startEpochMs) { "a range must not be empty: $startEpochMs..$endEpochMs" }
    }

    operator fun contains(atEpochMs: Long): Boolean = atEpochMs >= startEpochMs && atEpochMs < endEpochMs
}

data class SongPlays(
    val songId: SongId,
    val plays: Int,
)

data class ArtistPlays(
    val artistId: ArtistId,
    val plays: Int,
)

data class AlbumPlays(
    val albumId: AlbumId,
    val plays: Int,
)

/** The first play a song ever had in the whole log. */
data class FirstPlay(
    val songId: SongId,
    val firstPlayedAtEpochMs: Long,
)

/** Plays that started in one [ListeningAggregates.BUCKET_MS] bucket; [bucketStartEpochMs] is its start. */
data class TimeBucketPlays(
    val bucketStartEpochMs: Long,
    val plays: Int,
)

/**
 * The listening log as numbers, one period at a time (design §7.10, ADR 0088). Every method is an aggregate over
 * the rows of the range, so no screen loads the log; [playsInOrder] is the single exception, for rules that read
 * neighbouring rows. Every log row is one play, whatever its source or how it ended. Rankings are by plays, then
 * by id. Implemented by `:data`; the contract is `ListeningAggregatesContract`.
 */
interface ListeningAggregates {
    /** Songs by plays, best first, at most [limit] ([ALL] for every song). */
    suspend fun songPlays(
        range: TimeRange,
        limit: Int,
    ): List<SongPlays>

    /** Main artists (the song's artist tag) by plays, best first. */
    suspend fun artistPlays(
        range: TimeRange,
        limit: Int,
    ): List<ArtistPlays>

    /** Albums by plays, best first; songs without an album are left out. */
    suspend fun albumPlays(
        range: TimeRange,
        limit: Int,
    ): List<AlbumPlays>

    /** Time listened in the range, by [ListenedTime.countedMs] row by row. */
    suspend fun listenedMs(range: TimeRange): Long

    /** Songs whose first play in the whole log is in [range], earliest first, at most [limit]. */
    suspend fun firstPlays(
        range: TimeRange,
        limit: Int,
    ): List<FirstPlay>

    /** How many songs [firstPlays] would list without a limit. */
    suspend fun firstPlayCount(range: TimeRange): Int

    /** Plays per [BUCKET_MS] bucket, oldest first, empty buckets left out. */
    suspend fun playsPerBucket(range: TimeRange): List<TimeBucketPlays>

    /** The rows of the range, oldest first, equal times in logging order. Reads rows: for sequence rules only. */
    suspend fun playsInOrder(range: TimeRange): List<PlayEvent>

    /** The start of the oldest row of the whole log, or null when it is empty. */
    suspend fun earliestPlayAt(): Long?

    companion object {
        const val ALL: Int = Int.MAX_VALUE

        /** A quarter of an hour: every UTC offset in use is a whole number of these, so local hours stay exact. */
        const val BUCKET_MS: Long = 15L * 60 * 1000
    }
}
