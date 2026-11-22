package dev.tomasflopes.musicplayer.data.repo

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.stats.AlbumPlays
import dev.tomasflopes.musicplayer.core.model.stats.ArtistPlays
import dev.tomasflopes.musicplayer.core.model.stats.FirstPlay
import dev.tomasflopes.musicplayer.core.model.stats.ListenedTime
import dev.tomasflopes.musicplayer.core.model.stats.ListeningAggregates
import dev.tomasflopes.musicplayer.core.model.stats.SongPlays
import dev.tomasflopes.musicplayer.core.model.stats.TimeBucketPlays
import dev.tomasflopes.musicplayer.core.model.stats.TimeRange
import dev.tomasflopes.musicplayer.data.db.MusicDatabase

/** [ListeningAggregates] over Room: one GROUP BY query per method, run by Room off the main thread (ADR 0088). */
class RoomListeningAggregates(
    db: MusicDatabase,
) : ListeningAggregates {
    private val dao = db.listeningStatsDao()

    override suspend fun songPlays(
        range: TimeRange,
        limit: Int,
    ): List<SongPlays> =
        dao.songPlays(range.startEpochMs, range.endEpochMs, limit).map {
            SongPlays(SongId(it.id), it.plays)
        }

    override suspend fun artistPlays(
        range: TimeRange,
        limit: Int,
    ): List<ArtistPlays> =
        dao.artistPlays(range.startEpochMs, range.endEpochMs, limit).map { ArtistPlays(ArtistId(it.id), it.plays) }

    override suspend fun albumPlays(
        range: TimeRange,
        limit: Int,
    ): List<AlbumPlays> =
        dao.albumPlays(range.startEpochMs, range.endEpochMs, limit).map {
            AlbumPlays(AlbumId(it.id), it.plays)
        }

    override suspend fun listenedMs(range: TimeRange): Long =
        dao.listenedMs(range.startEpochMs, range.endEpochMs, ListenedTime.MAX_SONG_MS)

    override suspend fun firstPlays(
        range: TimeRange,
        limit: Int,
    ): List<FirstPlay> =
        dao.firstPlays(range.startEpochMs, range.endEpochMs, limit).map {
            FirstPlay(SongId(it.songId), it.firstAt)
        }

    override suspend fun firstPlayCount(range: TimeRange): Int =
        dao.firstPlayCount(range.startEpochMs, range.endEpochMs)

    override suspend fun playsPerBucket(range: TimeRange): List<TimeBucketPlays> =
        dao
            .playsPerBucket(range.startEpochMs, range.endEpochMs, ListeningAggregates.BUCKET_MS)
            .map { TimeBucketPlays(it.bucketStart, it.plays) }

    override suspend fun playsInOrder(range: TimeRange): List<PlayEvent> =
        dao.rowsInOrder(range.startEpochMs, range.endEpochMs).map { it.toDomain() }

    override suspend fun earliestPlayAt(): Long? = dao.earliestPlayAt()
}
