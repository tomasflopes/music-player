package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.PlayEventEntity

/** An id with how many log rows it has. */
data class IdPlays(
    val id: Long,
    val plays: Int,
)

data class FirstPlayRow(
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "first_at") val firstAt: Long,
)

data class BucketRow(
    @ColumnInfo(name = "bucket_start") val bucketStart: Long,
    val plays: Int,
)

/**
 * Aggregates over the listening log for Wrapped (ADR 0088). Read only: every query is a GROUP BY or a single
 * range scan on `started_at`, and the ranges are half-open (`>= from AND < to`). Ranking is by plays, then by id.
 */
@Dao
interface ListeningStatsDao {
    @Query(
        """
        SELECT song_id AS id, COUNT(*) AS plays FROM play_event
        WHERE started_at >= :from AND started_at < :to
        GROUP BY song_id ORDER BY plays DESC, song_id ASC LIMIT :limit
        """,
    )
    suspend fun songPlays(
        from: Long,
        to: Long,
        limit: Int,
    ): List<IdPlays>

    @Query(
        """
        SELECT s.artist_id AS id, COUNT(*) AS plays FROM play_event p JOIN song s ON s.id = p.song_id
        WHERE p.started_at >= :from AND p.started_at < :to
        GROUP BY s.artist_id ORDER BY plays DESC, s.artist_id ASC LIMIT :limit
        """,
    )
    suspend fun artistPlays(
        from: Long,
        to: Long,
        limit: Int,
    ): List<IdPlays>

    @Query(
        """
        SELECT s.album_id AS id, COUNT(*) AS plays FROM play_event p JOIN song s ON s.id = p.song_id
        WHERE p.started_at >= :from AND p.started_at < :to AND s.album_id IS NOT NULL
        GROUP BY s.album_id ORDER BY plays DESC, s.album_id ASC LIMIT :limit
        """,
    )
    suspend fun albumPlays(
        from: Long,
        to: Long,
        limit: Int,
    ): List<IdPlays>

    /** `ListenedTime.countedMs` in SQL: a possible row counts what was listened, up to the duration. */
    @Query(
        """
        SELECT COALESCE(SUM(MIN(listened_ms, song_duration_ms)), 0) FROM play_event
        WHERE started_at >= :from AND started_at < :to
          AND listened_ms > 0 AND song_duration_ms > 0 AND song_duration_ms <= :maxSongMs
        """,
    )
    suspend fun listenedMs(
        from: Long,
        to: Long,
        maxSongMs: Long,
    ): Long

    @Query(
        """
        SELECT song_id, MIN(started_at) AS first_at FROM play_event
        GROUP BY song_id HAVING MIN(started_at) >= :from AND MIN(started_at) < :to
        ORDER BY first_at ASC, song_id ASC LIMIT :limit
        """,
    )
    suspend fun firstPlays(
        from: Long,
        to: Long,
        limit: Int,
    ): List<FirstPlayRow>

    @Query(
        """
        SELECT COUNT(*) FROM (
          SELECT song_id FROM play_event
          GROUP BY song_id HAVING MIN(started_at) >= :from AND MIN(started_at) < :to
        )
        """,
    )
    suspend fun firstPlayCount(
        from: Long,
        to: Long,
    ): Int

    @Query(
        """
        SELECT (started_at / :bucketMs) * :bucketMs AS bucket_start, COUNT(*) AS plays FROM play_event
        WHERE started_at >= :from AND started_at < :to
        GROUP BY started_at / :bucketMs ORDER BY bucket_start ASC
        """,
    )
    suspend fun playsPerBucket(
        from: Long,
        to: Long,
        bucketMs: Long,
    ): List<BucketRow>

    /** Rows, not an aggregate: only for rules that read neighbouring rows (most skipped). */
    @Query("SELECT * FROM play_event WHERE started_at >= :from AND started_at < :to ORDER BY started_at, id")
    suspend fun rowsInOrder(
        from: Long,
        to: Long,
    ): List<PlayEventEntity>

    @Query("SELECT MIN(started_at) FROM play_event")
    suspend fun earliestPlayAt(): Long?
}
