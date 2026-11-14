package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.taste.config.BurstRule
import dev.tomasflopes.musicplayer.core.taste.score.PlayOutcome.COMPLETED
import dev.tomasflopes.musicplayer.core.taste.score.PlayOutcome.SKIPPED_EARLY
import dev.tomasflopes.musicplayer.core.taste.score.PlayOutcome.SKIPPED_MID
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class BrowsingBurstsTest {
    private fun row(startedAtMs: Long) =
        PlayEvent(SongId(1), startedAtMs, 0, 200_000, EndedBy.SKIPPED, PlaySource.ALBUM, StartedBy.AUTO, false)

    private fun softened(
        outcomes: List<PlayOutcome>,
        vararg startedAtMs: Long,
        rule: BurstRule = BurstRule(),
    ) = BrowsingBursts(rule).softened(startedAtMs.map(::row), outcomes)

    @Test
    fun `three quick skips in a row are softened`() {
        assertArrayEquals(
            booleanArrayOf(true, true, true),
            softened(listOf(SKIPPED_EARLY, SKIPPED_EARLY, SKIPPED_EARLY), 0, 5_000, 10_000),
        )
    }

    @Test
    fun `two quick skips are not`() {
        assertArrayEquals(
            booleanArrayOf(false, false),
            softened(listOf(SKIPPED_EARLY, SKIPPED_EARLY), 0, 5_000),
        )
    }

    @Test
    fun `four quick skips are all softened`() {
        assertArrayEquals(
            booleanArrayOf(true, true, true, true),
            softened(List(4) { SKIPPED_EARLY }, 0, 5_000, 10_000, 15_000),
        )
    }

    @Test
    fun `a long play between breaks the run`() {
        assertArrayEquals(
            booleanArrayOf(false, false, false, false),
            softened(listOf(SKIPPED_EARLY, SKIPPED_EARLY, COMPLETED, SKIPPED_EARLY), 0, 5_000, 10_000, 15_000),
        )
    }

    @Test
    fun `a skip that got past 15 s is not a quick skip`() {
        assertArrayEquals(
            booleanArrayOf(false, false, false, false),
            softened(listOf(SKIPPED_EARLY, SKIPPED_MID, SKIPPED_EARLY, SKIPPED_EARLY), 0, 5_000, 10_000, 15_000),
        )
    }

    @Test
    fun `a gap over 60 s breaks the run`() {
        assertArrayEquals(
            booleanArrayOf(true, true, true, false),
            softened(List(4) { SKIPPED_EARLY }, 0, 5_000, 10_000, 100_000),
        )
    }

    @Test
    fun `the song played after the burst is not softened`() {
        assertArrayEquals(
            booleanArrayOf(true, true, true, false),
            softened(listOf(SKIPPED_EARLY, SKIPPED_EARLY, SKIPPED_EARLY, COMPLETED), 0, 5_000, 10_000, 15_000),
        )
    }

    @Test
    fun `the minimum run length comes from the rule`() {
        assertArrayEquals(
            booleanArrayOf(true, true),
            softened(listOf(SKIPPED_EARLY, SKIPPED_EARLY), 0, 5_000, rule = BurstRule(minSkips = 2)),
        )
    }
}
