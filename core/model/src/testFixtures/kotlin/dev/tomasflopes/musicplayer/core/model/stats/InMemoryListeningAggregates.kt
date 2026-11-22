package dev.tomasflopes.musicplayer.core.model.stats

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent

/** The reference implementation: the rules written the plain way, over lists. [append] adds a row, like a play. */
class InMemoryListeningAggregates(
    songs: List<ContractSong>,
    plays: List<PlayEvent>,
) : ListeningAggregates {
    private val log = plays.toMutableList()
    private val bySong = songs.associateBy { it.songId }

    fun append(event: PlayEvent) {
        log += event
    }

    private fun inRange(range: TimeRange) = log.filter { it.startedAtEpochMs in range }

    override suspend fun songPlays(
        range: TimeRange,
        limit: Int,
    ): List<SongPlays> =
        inRange(range)
            .groupingBy { it.songId.value }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { SongPlays(SongId(it.key), it.value) }

    override suspend fun artistPlays(
        range: TimeRange,
        limit: Int,
    ): List<ArtistPlays> =
        inRange(range)
            .mapNotNull { bySong[it.songId.value]?.artistId }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { ArtistPlays(ArtistId(it.key), it.value) }

    override suspend fun albumPlays(
        range: TimeRange,
        limit: Int,
    ): List<AlbumPlays> =
        inRange(range)
            .mapNotNull { bySong[it.songId.value]?.albumId }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { AlbumPlays(AlbumId(it.key), it.value) }

    override suspend fun listenedMs(range: TimeRange): Long =
        inRange(range).sumOf { ListenedTime.countedMs(it.listenedMs, it.songDurationMs) }

    private fun firstPlaysInRange(range: TimeRange): List<FirstPlay> =
        log
            .groupBy { it.songId.value }
            .map { (id, rows) -> FirstPlay(SongId(id), rows.minOf { it.startedAtEpochMs }) }
            .filter { it.firstPlayedAtEpochMs in range }
            .sortedWith(compareBy({ it.firstPlayedAtEpochMs }, { it.songId.value }))

    override suspend fun firstPlays(
        range: TimeRange,
        limit: Int,
    ): List<FirstPlay> = firstPlaysInRange(range).take(limit)

    override suspend fun firstPlayCount(range: TimeRange): Int = firstPlaysInRange(range).size

    override suspend fun playsPerBucket(range: TimeRange): List<TimeBucketPlays> =
        inRange(range)
            .groupingBy { it.startedAtEpochMs / ListeningAggregates.BUCKET_MS }
            .eachCount()
            .toSortedMap()
            .map { (bucket, plays) -> TimeBucketPlays(bucket * ListeningAggregates.BUCKET_MS, plays) }

    override suspend fun playsInOrder(range: TimeRange): List<PlayEvent> =
        inRange(range).sortedBy { it.startedAtEpochMs }

    override suspend fun earliestPlayAt(): Long? = log.minOfOrNull { it.startedAtEpochMs }
}
