package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.taste.config.StatsConfig
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MostSkippedTest {
    private val mostSkipped = MostSkipped(TasteConfig(), StatsConfig())

    private fun songs(vararg rows: dev.tomasflopes.musicplayer.core.model.log.PlayEvent) = mostSkipped.of(rows.toList())

    @Test
    fun `songs rank by real skips, then by song id`() {
        val result =
            songs(
                skip(5, 0, 80_000),
                skip(5, 600_000, 80_000),
                skip(5, 1_200_000, 80_000),
                skip(2, 1_800_000, 90_000),
                skip(2, 2_400_000, 90_000),
                skip(1, 3_000_000, 90_000),
                skip(1, 3_600_000, 90_000),
            )

        assertEquals(listOf(SongSkips(SongId(5), 3), SongSkips(SongId(1), 2), SongSkips(SongId(2), 2)), result)
    }

    @Test
    fun `a completed or stopped play is not a skip`() {
        val result =
            songs(
                skip(1, 0, 80_000),
                skip(1, 600_000, 80_000),
                row(1, 1_200_000),
                row(1, 1_800_000, listenedMs = 50_000, endedBy = EndedBy.STOPPED),
            )

        assertEquals(listOf(SongSkips(SongId(1), 2)), result)
    }

    @Test
    fun `a skip past 80 percent of the song counts as finished, not skipped`() {
        assertEquals(emptyList<SongSkips>(), songs(skip(1, 0, 170_000), skip(1, 600_000, 170_000)))
    }

    @Test
    fun `three quick skips close together are a browsing burst and do not count`() {
        val burst =
            songs(
                skip(1, 0, 3_000),
                skip(1, 10_000, 3_000),
                skip(1, 20_000, 3_000),
                skip(1, 600_000, 80_000),
                skip(1, 1_200_000, 80_000),
            )

        assertEquals(listOf(SongSkips(SongId(1), 2)), burst)
    }

    @Test
    fun `two quick skips are not a burst and count`() {
        assertEquals(
            listOf(SongSkips(SongId(1), 2)),
            songs(skip(1, 0, 3_000), skip(1, 10_000, 3_000)),
        )
    }

    @Test
    fun `a finished play between quick skips breaks the run, so none is a burst`() {
        val result = songs(skip(1, 0, 3_000), skip(1, 10_000, 3_000), row(2, 20_000), skip(1, 30_000, 3_000))

        assertEquals(listOf(SongSkips(SongId(1), 3)), result)
    }

    @Test
    fun `a song skipped once is not listed`() {
        assertEquals(emptyList<SongSkips>(), songs(skip(1, 0, 80_000)))
    }

    @Test
    fun `only the configured number of songs is kept`() {
        val rows =
            (1L..8L).flatMap { id ->
                listOf(skip(id, id * 1_000_000, 80_000), skip(id, id * 1_000_000 + 600_000, 80_000))
            }

        assertEquals(5, mostSkipped.of(rows).size)
    }

    @Test
    fun `no rows give no skipped songs`() = assertEquals(emptyList<SongSkips>(), songs())
}
