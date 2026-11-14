package dev.tomasflopes.musicplayer.core.taste.score

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every [ScoreBoard] must pass these. A cached board's test class extends this one too (ADR 0023). */
abstract class ScoreBoardContract {
    /** A board over a log with rows for at least two different songs. */
    abstract fun board(): ScoreBoard

    @Test
    fun `a log with rows gives scores`() =
        runTest {
            assertTrue(board().observeRanked().first().size >= 2)
        }

    @Test
    fun `ranked is best first`() =
        runTest {
            val ranked = board().observeRanked().first()

            assertEquals(ranked.sortedByDescending { it.score }.map { it.songId }, ranked.map { it.songId })
        }

    @Test
    fun `no song appears twice`() =
        runTest {
            val ranked = board().observeRanked().first()

            assertEquals(ranked.size, ranked.map { it.songId }.toSet().size)
        }
}
