package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.EmptyCoroutineContext

/** ADR 0084: skips the app caused (mix and radio rows) count `skipShare` of their weight; nothing else changes. */
class TasteScorerSkipShareTest {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private val shared = TasteScorer(TasteConfig(skipShare = 0.3))
    private val full = TasteScorer(TasteConfig(skipShare = 1.0))

    private fun play(
        song: Long,
        at: Long,
        source: PlaySource,
        listenedMs: Long,
        endedBy: EndedBy = EndedBy.SKIPPED,
        startedBy: StartedBy = StartedBy.NAV,
        shuffle: Boolean = false,
    ) = PlayEvent(SongId(song), at, listenedMs, 200_000, endedBy, source, startedBy, shuffle)

    private fun quickSkip(
        song: Long,
        at: Long,
        source: PlaySource,
    ) = play(song, at, source, listenedMs = 5_000)

    private fun completed(
        song: Long,
        at: Long,
        source: PlaySource,
    ) = play(song, at, source, 200_000, EndedBy.COMPLETED, StartedBy.AUTO)

    private fun TasteScorer.scoreOf(
        song: Long,
        events: List<PlayEvent>,
    ) = score(events, now).single { it.songId == SongId(song) }.score

    @Test
    fun `an early skip in a mix counts 0_3 of -2`() {
        assertEquals(-0.6, shared.scoreOf(1, listOf(quickSkip(1, now, PlaySource.MIX))), 1e-9)
    }

    @Test
    fun `an early skip in a radio counts 0_3 of -2`() {
        assertEquals(-0.6, shared.scoreOf(1, listOf(quickSkip(1, now, PlaySource.RADIO))), 1e-9)
    }

    @Test
    fun `a mid skip in a mix is scaled too`() {
        val midSkip = play(1, now, PlaySource.MIX, listenedMs = 60_000)

        assertEquals(-0.3, shared.scoreOf(1, listOf(midSkip)), 1e-9)
    }

    @Test
    fun `rows from every other source score exactly as with share 1`() {
        val others = PlaySource.entries - setOf(PlaySource.MIX, PlaySource.RADIO)
        val events =
            others.flatMapIndexed { index, source ->
                val at = now - index * day
                listOf(
                    quickSkip(index * 10L + 1, at, source),
                    play(index * 10L + 2, at, source, 60_000),
                    play(index * 10L + 3, at, source, 190_000, EndedBy.SKIPPED, StartedBy.PICKED),
                    completed(index * 10L + 4, at, source),
                )
            }

        assertEquals(full.score(events, now), shared.score(events, now))
    }

    @Test
    fun `a shuffle skip stays at -2 even though shuffle also counts completions`() {
        val skip = play(1, now, PlaySource.SHUFFLE_ALL, listenedMs = 5_000, shuffle = true)

        assertEquals(-2.0, shared.scoreOf(1, listOf(skip)))
    }

    @Test
    fun `a scorer built with share 1 scores mix and radio rows at full weight`() {
        assertEquals(-2.0, full.scoreOf(1, listOf(quickSkip(1, now, PlaySource.MIX))))
        assertEquals(-2.0, full.scoreOf(1, listOf(quickSkip(1, now, PlaySource.RADIO))))
    }

    @Test
    fun `positives are untouched, a completed mix play is +1 and a picked mix play keeps its +3`() {
        assertEquals(1.0, shared.scoreOf(1, listOf(completed(1, now, PlaySource.MIX))))
        assertEquals(1.0, shared.scoreOf(2, listOf(completed(2, now, PlaySource.RADIO))))
        val pickedMidSkip = play(3, now, PlaySource.MIX, 60_000, EndedBy.SKIPPED, StartedBy.PICKED)

        assertEquals(3.0 - 0.3, shared.scoreOf(3, listOf(pickedMidSkip)), 1e-9)
    }

    @Test
    fun `a burst and the share stack, -2 x 0_1 x 0_3`() {
        val burst =
            listOf(
                quickSkip(1, now, PlaySource.MIX),
                quickSkip(2, now + 5_000, PlaySource.MIX),
                quickSkip(3, now + 10_000, PlaySource.MIX),
            )

        assertEquals(-0.06, shared.scoreOf(1, burst), 1e-9)
        assertEquals(-0.2, full.scoreOf(1, burst), 1e-9)
    }

    @Test
    fun `a completed play still forgives the earlier mix skips of that song`() {
        val events = listOf(quickSkip(1, now - 30 * day, PlaySource.MIX), completed(1, now, PlaySource.MIX))

        assertEquals(1.0, shared.scoreOf(1, events))
    }

    @Test
    fun `a mix skip after a completion still counts, scaled`() {
        val events = listOf(completed(1, now - 30 * day, PlaySource.MIX), quickSkip(1, now, PlaySource.MIX))

        assertEquals(0.5 - 0.6, shared.scoreOf(1, events), 1e-9)
    }

    @Test
    fun `Rewind's past scores shift only for songs with mix or radio skips`() {
        val asOf = now - 60 * day
        val log =
            listOf(
                quickSkip(1, asOf - 5 * day, PlaySource.MIX),
                quickSkip(2, asOf - 5 * day, PlaySource.RADIO),
                quickSkip(3, asOf - 5 * day, PlaySource.SHUFFLE_ALL),
                completed(4, asOf - 5 * day, PlaySource.ALBUM),
            )
        val signals = TasteSignals(plays = log)

        val before = PastScorer(TasteConfig(skipShare = 1.0)).songsAsOf(signals, asOf).associateBy { it.songId.value }
        val after = PastScorer(TasteConfig(skipShare = 0.3)).songsAsOf(signals, asOf).associateBy { it.songId.value }

        assertNotEquals(before.getValue(1).score, after.getValue(1).score)
        assertNotEquals(before.getValue(2).score, after.getValue(2).score)
        assertEquals(before.getValue(3), after.getValue(3))
        assertEquals(before.getValue(4), after.getValue(4))
    }

    @Test
    fun `Top 100 scores shift only for songs with mix or radio skips`() =
        runTest {
            val log =
                FakePlayEventSource(
                    listOf(
                        quickSkip(1, now - day, PlaySource.MIX),
                        quickSkip(2, now - day, PlaySource.RADIO),
                        quickSkip(3, now - day, PlaySource.SHUFFLE_ALL),
                        completed(4, now - day, PlaySource.SEARCH),
                    ),
                )
            val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)

            suspend fun board(share: Double): Map<Long, Double> {
                val config = TasteConfig(skipShare = share)
                return OnDemandScoreBoard(log, TasteScorer(config), config, clock, EmptyCoroutineContext)
                    .observeRanked()
                    .first()
                    .associate { it.songId.value to it.score }
            }

            val before = board(1.0)
            val after = board(0.3)

            assertNotEquals(before.getValue(1), after.getValue(1))
            assertNotEquals(before.getValue(2), after.getValue(2))
            assertEquals(before.getValue(3), after.getValue(3))
            assertEquals(before.getValue(4), after.getValue(4))
        }
}
