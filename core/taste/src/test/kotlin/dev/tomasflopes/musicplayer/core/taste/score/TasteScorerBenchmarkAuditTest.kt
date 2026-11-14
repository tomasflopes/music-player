package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random

/**
 * Audit 2026-11-14, S16: how long one recompute of the whole board takes (ADR 0023 switches to a cached table at
 * about 150 ms on the phone). Times [TasteScorer.score] only, on the machine running the tests, not the Room read
 * and not the phone. The bound is a loose sanity check; the number is printed and written to build/.
 */
class TasteScorerBenchmarkAuditTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun log(
        songs: Int,
        rows: Int,
    ): List<PlayEvent> {
        val random = Random(16)
        val sources = PlaySource.entries
        return List(rows) {
            val ended = EndedBy.entries[random.nextInt(EndedBy.entries.size)]
            val duration = 120_000L + random.nextLong(240_000)
            PlayEvent(
                songId = SongId(1L + random.nextInt(songs)),
                startedAtEpochMs = now - random.nextLong(240 * day),
                listenedMs = if (ended == EndedBy.COMPLETED) duration else random.nextLong(duration),
                songDurationMs = duration,
                endedBy = ended,
                source = sources[random.nextInt(sources.size)],
                startedBy = StartedBy.entries[random.nextInt(StartedBy.entries.size)],
                shuffle = random.nextBoolean(),
            )
        }.sortedBy { it.startedAtEpochMs }
    }

    private fun medianMs(events: List<PlayEvent>): Double {
        val scorer = TasteScorer()
        repeat(WARM_UP) { scorer.score(events, now) }
        val times =
            List(RUNS) {
                val start = System.nanoTime()
                scorer.score(events, now)
                (System.nanoTime() - start) / NANOS_PER_MS
            }
        return times.sorted()[RUNS / 2]
    }

    @Test
    fun `S16 scoring 16k songs over 36k and 200k rows`() {
        val medians = listOf(36_000, 200_000).associateWith { medianMs(log(songs = 16_000, rows = it)) }
        val report =
            medians.entries.joinToString("\n") { (rows, ms) ->
                "S16 TasteScorer.score 16000 songs, $rows rows: median %.1f ms of $RUNS runs".format(ms)
            }
        println(report)
        File("build/s16-benchmark.txt").apply { parentFile.mkdirs() }.writeText(report + "\n")

        medians.values.forEach { assertTrue(it < SANITY_BOUND_MS, "median $it ms is over the sanity bound") }
    }

    private companion object {
        const val WARM_UP = 5
        const val RUNS = 11
        const val NANOS_PER_MS = 1_000_000.0
        const val SANITY_BOUND_MS = 5_000.0
    }
}
