package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.stats.ContractSong
import dev.tomasflopes.musicplayer.core.model.stats.FirstPlay
import dev.tomasflopes.musicplayer.core.model.stats.InMemoryListeningAggregates
import dev.tomasflopes.musicplayer.core.taste.config.StatsConfig
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.time.ZoneId

class StatsComputerTest {
    private val library =
        (1L..7L).map { ContractSong(it, artistId = 10 + it, albumId = 100 + it) } +
            listOf(ContractSong(20, artistId = 10, albumId = 100), ContractSong(21, artistId = 10, albumId = 100))
    private val november = StatsPeriod.Month(YearMonth.of(2026, 11))
    private val october = november.previous()

    private fun computer(
        vararg rows: dev.tomasflopes.musicplayer.core.model.log.PlayEvent,
        zone: ZoneId = UTC,
    ) = StatsComputer(
        InMemoryListeningAggregates(library, rows.toList()),
        zone,
        StatsConfig(),
        MostSkipped(TasteConfig(), StatsConfig()),
        ComebackArtist(StatsConfig()),
    )

    @Test
    fun `only plays inside the month count, and the edges belong to the right month`() =
        runTest {
            val stats =
                computer(
                    row(20, epoch("2026-10-31T23:59:59.999Z")),
                    row(20, epoch("2026-11-01T00:00:00Z")),
                    row(21, epoch("2026-11-30T23:59:59.999Z")),
                    row(20, epoch("2026-12-01T00:00:00Z")),
                ).compute(november)

            assertEquals(2, stats.totalPlays)
        }

    @Test
    fun `the month is the owner's local month`() =
        runTest {
            val rows = arrayOf(row(20, epoch("2026-10-31T23:59:00Z")))

            assertEquals(1, computer(*rows, zone = ZoneId.of("Asia/Tokyo")).compute(november).totalPlays)
            assertEquals(0, computer(*rows, zone = UTC).compute(november).totalPlays)
        }

    @Test
    fun `a year counts January to December`() =
        runTest {
            val stats =
                computer(
                    row(20, epoch("2025-12-31T23:59:59Z")),
                    row(20, epoch("2026-01-01T00:00:00Z")),
                    row(21, epoch("2026-12-31T23:59:59Z")),
                ).compute(StatsPeriod.Year(2026))

            assertEquals(2, stats.totalPlays)
        }

    @Test
    fun `the top lists keep the configured number of entries, best first`() =
        runTest {
            val rows =
                (1L..7L).flatMap { id ->
                    List(id.toInt()) { n ->
                        row(
                            id,
                            epoch("2026-11-05T10:00:00Z") + id * 1000 + n,
                        )
                    }
                }

            val stats = computer(*rows.toTypedArray()).compute(november)

            assertEquals(listOf(7L, 6L, 5L, 4L, 3L), stats.topSongs.map { it.songId.value })
            assertEquals(listOf(17L, 16L, 15L, 14L, 13L), stats.topArtists.map { it.artistId.value })
            assertEquals(listOf(107L, 106L, 105L, 104L, 103L), stats.topAlbums.map { it.albumId.value })
        }

    @Test
    fun `minutes listened come from the clamped listened time`() =
        runTest {
            val stats =
                computer(
                    row(20, epoch("2026-11-05T10:00:00Z"), listenedMs = 90_000),
                    row(20, epoch("2026-11-05T11:00:00Z"), listenedMs = 150_000),
                    row(20, epoch("2026-11-05T12:00:00Z"), listenedMs = 999_000, durationMs = 0),
                ).compute(november)

            assertEquals(240_000L, stats.listenedMs)
            assertEquals(4L, stats.minutesListened)
        }

    @Test
    fun `a song is a new discovery only in the period of its first ever play`() =
        runTest {
            val computer =
                computer(
                    row(20, epoch("2026-10-05T10:00:00Z")),
                    row(20, epoch("2026-11-05T10:00:00Z")),
                    row(21, epoch("2026-11-06T10:00:00Z")),
                )

            assertEquals(1, computer.compute(october).newDiscoveries.count)
            assertEquals(
                listOf(FirstPlay(SongId(21), epoch("2026-11-06T10:00:00Z"))),
                computer.compute(november).newDiscoveries.first,
            )
        }

    @Test
    fun `the comeback artist is the largest rise against the previous period`() =
        runTest {
            val rows =
                listOf(row(20, epoch("2026-10-05T10:00:00Z")), row(20, epoch("2026-10-06T10:00:00Z"))) +
                    List(2) { row(20, epoch("2026-11-05T10:00:00Z") + it) } +
                    List(5) { row(3, epoch("2026-11-07T10:00:00Z") + it) }

            val comeback = computer(*rows.toTypedArray()).compute(november).comeback

            assertEquals(Comeback(ArtistId(13), plays = 5, previousPlays = 0), comeback)
        }

    @Test
    fun `most skipped ignores browsing bursts`() =
        runTest {
            val at = epoch("2026-11-05T10:00:00Z")
            val stats =
                computer(
                    skip(1, at, 80_000),
                    skip(1, at + 600_000, 80_000),
                    skip(2, at + 1_200_000, 3_000),
                    skip(2, at + 1_210_000, 3_000),
                    skip(2, at + 1_220_000, 3_000),
                ).compute(november)

            assertEquals(listOf(SongSkips(SongId(1), 2)), stats.mostSkipped)
        }

    @Test
    fun `the listening clock holds the plays of the period in local hours`() =
        runTest {
            val stats =
                computer(
                    row(20, epoch("2026-11-05T09:10:00Z")),
                    row(20, epoch("2026-10-05T09:10:00Z")),
                ).compute(november)

            assertEquals(1, stats.clock.playsByHour[9])
            assertEquals(1, stats.clock.total)
        }

    @Test
    fun `a period with no plays is empty and never fails`() =
        runTest {
            val stats = computer().compute(november)

            assertTrue(stats.isEmpty)
            assertEquals(emptyList<Any>(), stats.topSongs + stats.topArtists + stats.topAlbums + stats.mostSkipped)
            assertEquals(0L, stats.minutesListened)
            assertEquals(0, stats.newDiscoveries.count)
            assertEquals(0, stats.clock.total)
            assertNull(stats.comeback)
        }
}
