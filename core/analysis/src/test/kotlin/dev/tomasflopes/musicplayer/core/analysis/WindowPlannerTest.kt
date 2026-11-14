package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WindowPlannerTest {
    private val planner = WindowPlanner()

    @Test
    fun `a long song gets three 20 second windows centred at 25, 50 and 75 percent`() {
        val windows = planner.plan(durationMs = 200_000L)

        assertEquals(
            listOf(
                AnalysisWindow(40_000L, 20_000L),
                AnalysisWindow(90_000L, 20_000L),
                AnalysisWindow(140_000L, 20_000L),
            ),
            windows,
        )
    }

    @Test
    fun `windows stay inside a short song`() {
        val windows = planner.plan(durationMs = 30_000L)

        assertEquals(3, windows.size)
        assertTrue(windows.all { it.startMs >= 0L && it.startMs + it.lengthMs <= 30_000L })
    }

    @Test
    fun `a song shorter than one window becomes one window`() {
        assertEquals(listOf(AnalysisWindow(0L, 15_000L)), planner.plan(durationMs = 15_000L))
    }

    @Test
    fun `an empty song has no windows`() {
        assertTrue(planner.plan(durationMs = 0L).isEmpty())
    }
}
