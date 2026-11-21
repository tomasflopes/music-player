package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.score.FakePlayEventSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** ADR 0085: a mix's own rows, scored at full weight, become a [MixLearning]. */
class OnDemandMixScoreBoardTest {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private val mix = MixId(3)

    private fun row(
        song: Long,
        at: Long,
        listenedMs: Long,
        endedBy: EndedBy,
        mixId: Long? = mix.value,
        source: PlaySource = if (mixId == null) PlaySource.ALBUM else PlaySource.MIX,
    ) = PlayEvent(SongId(song), at, listenedMs, 200_000, endedBy, source, StartedBy.AUTO, false, mixId)

    private fun completed(
        song: Long,
        at: Long = now - day,
        mixId: Long? = mix.value,
    ) = row(song, at, 200_000, EndedBy.COMPLETED, mixId)

    private fun quickSkip(
        song: Long,
        at: Long = now - day,
        mixId: Long? = mix.value,
    ) = row(song, at, 5_000, EndedBy.SKIPPED, mixId)

    private fun midSkip(
        song: Long,
        at: Long = now - day,
    ) = row(song, at, 60_000, EndedBy.SKIPPED)

    private fun stopped(
        song: Long,
        at: Long = now - day,
    ) = row(song, at, 30_000, EndedBy.STOPPED)

    private fun board(
        vararg rows: PlayEvent,
        config: MixConfig = MixConfig(),
        // The port hands rows over oldest first; the fixtures below are written in reading order.
        source: FakePlayEventSource = FakePlayEventSource(rows.sortedBy { it.startedAtEpochMs }),
    ) = OnDemandMixScoreBoard(source, TasteConfig(), config)

    private suspend fun learn(
        vararg rows: PlayEvent,
        config: MixConfig = MixConfig(),
    ) = board(*rows, config = config).of(mix, now)

    @Test
    fun `a mix with no rows learned nothing`() =
        runTest {
            assertEquals(MixLearning.EMPTY, learn())
        }

    @Test
    fun `it asks the log once, for the decay horizon`() =
        runTest {
            val source = FakePlayEventSource()

            board(source = source).of(mix, now)

            assertEquals(listOf(now - 240 * day), source.requestedSince)
        }

    @Test
    fun `only the rows of this mix count`() =
        runTest {
            val learning =
                learn(
                    completed(1, mixId = 4),
                    completed(2, mixId = null),
                    completed(3),
                    quickSkip(5, mixId = 4),
                )

            assertEquals(listOf(SongId(3)), learning.liked)
            assertEquals(emptySet<SongId>(), learning.banned)
            assertEquals(setOf(SongId(3)), learning.playedInMix)
            assertEquals(1, learning.informativeRows)
        }

    @Test
    fun `a completed play is liked, best score first`() =
        runTest {
            val learning =
                learn(
                    completed(1, at = now - 2 * day),
                    completed(2, at = now - day),
                    completed(3, at = now - day),
                    completed(3, at = now - 3 * day),
                )

            // 3 has two plays, then 2 (played a day ago) before 1 (two days ago).
            assertEquals(listOf(SongId(3), SongId(2), SongId(1)), learning.liked)
        }

    @Test
    fun `a song needs a score of at least 0_5 to be liked`() =
        runTest {
            val weak = completed(1, at = now - 40 * day) // 1 x 0.5^(40/30) = 0.40
            val strong = completed(2, at = now - 30 * day) // exactly 0.5

            val learning = learn(weak, strong)

            assertEquals(listOf(SongId(2)), learning.liked)
        }

    @Test
    fun `skips count at full weight here, one early skip is -2 and not banned, two are`() =
        runTest {
            val one = learn(quickSkip(1, at = now))
            val two = learn(quickSkip(2, at = now), midSkip(2, at = now))

            assertEquals(emptySet<SongId>(), one.banned)
            assertEquals(setOf(SongId(2)), two.banned) // -2 + -1 = -3, banned at -3.0 or below
        }

    @Test
    fun `a banned song is never liked`() =
        runTest {
            val learning = learn(quickSkip(1, at = now), midSkip(1, at = now), completed(2))

            assertEquals(setOf(SongId(1)), learning.banned)
            assertEquals(listOf(SongId(2)), learning.liked)
        }

    @Test
    fun `a ban fades as the skips decay`() =
        runTest {
            val old = learn(quickSkip(1, at = now - 60 * day), midSkip(1, at = now - 60 * day)) // -3 x 0.25 = -0.75

            assertEquals(emptySet<SongId>(), old.banned)
        }

    @Test
    fun `songs played in this mix in the last 14 days are in the cool-down, whatever the outcome`() =
        runTest {
            val learning =
                learn(
                    completed(1, at = now - 13 * day),
                    quickSkip(2, at = now - 2 * day),
                    stopped(3, at = now - day),
                    completed(4, at = now - 15 * day),
                )

            assertEquals(setOf(SongId(1), SongId(2), SongId(3)), learning.playedInMix)
        }

    @Test
    fun `the cool-down window follows mixRepeatDays`() =
        runTest {
            val learning = learn(completed(1, at = now - 3 * day), config = MixConfig(mixRepeatDays = 2))

            assertEquals(emptySet<SongId>(), learning.playedInMix)
        }

    @Test
    fun `informative rows are the outcomes that say something and are not burst-softened`() =
        runTest {
            val learning =
                learn(
                    completed(1),
                    completed(2),
                    stopped(3), // neutral: says nothing
                    midSkip(4),
                    // a browsing burst of three quick skips inside a minute: softened, not informative
                    quickSkip(5, at = now - 10 * day),
                    quickSkip(6, at = now - 10 * day + 5_000),
                    quickSkip(7, at = now - 10 * day + 10_000),
                )

            assertEquals(3, learning.informativeRows)
        }

    @Test
    fun `a quick skip on its own is informative`() =
        runTest {
            assertEquals(1, learn(quickSkip(1)).informativeRows)
        }
}
