package dev.tomasflopes.musicplayer.core.model.stats

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import kotlinx.coroutines.runBlocking

/** A library song as the contract seeds it, with explicit ids so every implementation can use the same ones. */
data class ContractSong(
    val songId: Long,
    val artistId: Long,
    val albumId: Long?,
)

/** One named check; plain code, so JUnit 5 (`check`) and JUnit 4 (`deviceCheck`, Room) can both run it. */
class AggregatesCheck(
    val name: String,
    val run: () -> Unit,
)

/** Builds a fresh [ListeningAggregates] holding exactly these songs and log rows (rows in logging order). */
fun interface AggregatesFactory {
    fun create(
        songs: List<ContractSong>,
        plays: List<PlayEvent>,
    ): ListeningAggregates
}

/**
 * What every [ListeningAggregates] must do (ADR 0088): count every log row once whatever its source or outcome,
 * rank by plays then by id, treat a range as half-open, count main artists and albums through the song, apply the
 * listened-time rule, find first-ever plays against the whole log, bucket by time, and keep log order.
 */
object ListeningAggregatesContract {
    private const val B = ListeningAggregates.BUCKET_MS
    private val songs =
        listOf(
            ContractSong(1, artistId = 10, albumId = 100),
            ContractSong(2, artistId = 10, albumId = 100),
            ContractSong(3, artistId = 11, albumId = null),
            ContractSong(4, artistId = 12, albumId = 101),
        )
    private val everything = TimeRange(0, 1_000_000_000)

    fun checks(factory: AggregatesFactory): List<AggregatesCheck> =
        listOf(
            AggregatesCheck("every play counts once, whatever its source or how it ended") { everyPlayCounts(factory) },
            AggregatesCheck("songs rank by plays then song id, and the limit keeps the best") { songRanking(factory) },
            AggregatesCheck("a range includes its start and leaves out its end") { halfOpen(factory) },
            AggregatesCheck("artists are the main artist of the song, summed") { artists(factory) },
            AggregatesCheck("albums leave out songs without one") { albums(factory) },
            AggregatesCheck("listened time clamps to the duration and skips impossible rows") { listened(factory) },
            AggregatesCheck("first plays are checked against the whole log") { firstPlays(factory) },
            AggregatesCheck("plays are counted per time bucket, oldest first") { buckets(factory) },
            AggregatesCheck("rows in order keep the logging order of equal times") { inOrder(factory) },
            AggregatesCheck("the earliest play is the oldest start, or none") { earliest(factory) },
            AggregatesCheck("an empty log gives empty answers") { emptyLog(factory) },
        )

    private fun play(
        song: Long,
        at: Long,
        listenedMs: Long = 200_000,
        durationMs: Long = 200_000,
        endedBy: EndedBy = EndedBy.COMPLETED,
        source: PlaySource = PlaySource.SHUFFLE_ALL,
    ) = PlayEvent(SongId(song), at, listenedMs, durationMs, endedBy, source, StartedBy.AUTO, shuffle = true)

    private fun expect(
        expected: Any?,
        actual: Any?,
    ) = check(expected == actual) { "expected $expected but was $actual" }

    private fun everyPlayCounts(factory: AggregatesFactory) {
        val plays =
            listOf(
                play(1, 10),
                play(1, 20, source = PlaySource.MIX),
                play(1, 30, source = PlaySource.PLAYLIST),
                play(1, 40, listenedMs = 3_000, endedBy = EndedBy.SKIPPED),
                play(2, 50),
            )
        val aggregates = factory.create(songs, plays)
        expect(listOf(SongPlays(SongId(1), 4), SongPlays(SongId(2), 1)), songPlays(aggregates, everything))
    }

    private fun songRanking(factory: AggregatesFactory) {
        val plays = listOf(play(4, 1), play(3, 2), play(2, 3), play(1, 4), play(1, 5))
        val aggregates = factory.create(songs, plays)
        val expected = listOf(SongPlays(SongId(1), 2), SongPlays(SongId(2), 1), SongPlays(SongId(3), 1))
        expect(expected, runBlocking { aggregates.songPlays(everything, limit = 3) })
    }

    private fun halfOpen(factory: AggregatesFactory) {
        val aggregates = factory.create(songs, listOf(play(1, 99), play(2, 100), play(3, 199), play(4, 200)))
        expect(
            listOf(SongId(2), SongId(3)),
            songPlays(aggregates, TimeRange(100, 200)).map { it.songId },
        )
    }

    private fun artists(factory: AggregatesFactory) {
        val aggregates = factory.create(songs, listOf(play(1, 1), play(2, 2), play(3, 3), play(4, 4), play(4, 5)))
        val expected =
            listOf(ArtistPlays(ArtistId(10), 2), ArtistPlays(ArtistId(12), 2), ArtistPlays(ArtistId(11), 1))
        expect(expected, runBlocking { aggregates.artistPlays(everything, ListeningAggregates.ALL) })
    }

    private fun albums(factory: AggregatesFactory) {
        val aggregates = factory.create(songs, listOf(play(1, 1), play(2, 2), play(3, 3), play(4, 4)))
        val expected = listOf(AlbumPlays(AlbumId(100), 2), AlbumPlays(AlbumId(101), 1))
        expect(expected, runBlocking { aggregates.albumPlays(everything, ListeningAggregates.ALL) })
    }

    private fun listened(factory: AggregatesFactory) {
        val plays =
            listOf(
                play(1, 1, listenedMs = 50_000),
                play(1, 2, listenedMs = 300_000),
                play(1, 3, listenedMs = 100, durationMs = 0),
                play(1, 4, listenedMs = -5),
                play(1, 5, listenedMs = 10_000, durationMs = ListenedTime.MAX_SONG_MS + 1),
            )
        val aggregates = factory.create(songs, plays)
        expect(250_000L, runBlocking { aggregates.listenedMs(everything) })
    }

    private fun firstPlays(factory: AggregatesFactory) {
        val aggregates =
            factory.create(
                songs,
                listOf(play(1, 100), play(1, 4_500), play(2, 5_000), play(2, 6_000), play(3, 7_000)),
            )
        val range = TimeRange(4_000, 10_000)
        expect(
            listOf(FirstPlay(SongId(2), 5_000), FirstPlay(SongId(3), 7_000)),
            runBlocking { aggregates.firstPlays(range, ListeningAggregates.ALL) },
        )
        expect(listOf(FirstPlay(SongId(2), 5_000)), runBlocking { aggregates.firstPlays(range, 1) })
        expect(2, runBlocking { aggregates.firstPlayCount(range) })
    }

    private fun buckets(factory: AggregatesFactory) {
        val aggregates = factory.create(songs, listOf(play(1, 5 * B + 7), play(1, 2 * B + 5), play(2, 2 * B + 10)))
        expect(
            listOf(TimeBucketPlays(2 * B, 2), TimeBucketPlays(5 * B, 1)),
            runBlocking { aggregates.playsPerBucket(everything) },
        )
    }

    private fun inOrder(factory: AggregatesFactory) {
        val plays = listOf(play(2, 10), play(1, 10), play(3, 5), play(4, 500))
        val aggregates = factory.create(songs, plays)
        expect(
            listOf(SongId(3), SongId(2), SongId(1)),
            runBlocking { aggregates.playsInOrder(TimeRange(0, 100)) }.map { it.songId },
        )
    }

    private fun earliest(factory: AggregatesFactory) {
        expect(null, runBlocking { factory.create(songs, emptyList()).earliestPlayAt() })
        expect(5L, runBlocking { factory.create(songs, listOf(play(1, 9), play(2, 5))).earliestPlayAt() })
    }

    private fun emptyLog(factory: AggregatesFactory) {
        val aggregates = factory.create(songs, emptyList())
        runBlocking {
            expect(emptyList<SongPlays>(), aggregates.songPlays(everything, ListeningAggregates.ALL))
            expect(emptyList<ArtistPlays>(), aggregates.artistPlays(everything, ListeningAggregates.ALL))
            expect(emptyList<AlbumPlays>(), aggregates.albumPlays(everything, ListeningAggregates.ALL))
            expect(0L, aggregates.listenedMs(everything))
            expect(0, aggregates.firstPlayCount(everything))
            expect(emptyList<TimeBucketPlays>(), aggregates.playsPerBucket(everything))
            expect(emptyList<PlayEvent>(), aggregates.playsInOrder(everything))
        }
    }

    private fun songPlays(
        aggregates: ListeningAggregates,
        range: TimeRange,
    ) = runBlocking { aggregates.songPlays(range, ListeningAggregates.ALL) }
}
