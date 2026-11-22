package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** At 120 BPM a bar is 2 s and a phrase 8 s; the first beat is at 0. */
class EaseBackTest {
    private val config = TransitionConfig()
    private val grid = BeatGrid(0.0, 120.0, 4, 16)

    @Test
    fun `no stretch needs no ease-back`() {
        assertTrue(EaseBack.steps(config, 1.0, grid, 10_000.0).isEmpty())
    }

    @Test
    fun `a small stretch eases over eight bars, one step on each bar start`() {
        val steps = EaseBack.steps(config, 0.97, grid, 16_000.0)

        assertEquals(8, steps.size)
        assertEquals((0 until 8).map { 16_000L + it * 2_000L }, steps.map { it.atMs })
        assertEquals(1f, steps.last().speed)
        assertTrue(steps.first().speed > 0.97f)
    }

    @Test
    fun `no step is larger than the maximum step`() {
        for (speed in listOf(0.92, 0.95, 0.99, 1.01, 1.08)) {
            val steps = EaseBack.steps(config, speed, grid, 0.0)
            val all = listOf(speed.toFloat()) + steps.map { it.speed }
            all.zipWithNext().forEach { (a, b) ->
                assertTrue(abs(b - a) <= config.maxSpeedStep.toFloat() + 1e-6f, "$speed: ${abs(b - a)}")
            }
            assertEquals(1f, all.last())
        }
    }

    @Test
    fun `a stretch of eight percent takes as many bars as its steps need`() {
        val steps = EaseBack.steps(config, 0.92, grid, 0.0)

        assertEquals(16, steps.size)
        assertEquals(30_000L, steps.last().atMs)
    }

    @Test
    fun `speeds move monotonically toward one`() {
        val up = EaseBack.steps(config, 0.95, grid, 0.0).map { it.speed }
        val down = EaseBack.steps(config, 1.05, grid, 0.0).map { it.speed }

        assertEquals(up.sorted(), up)
        assertEquals(down.sortedDescending(), down)
    }

    @Test
    fun `the single step variant sets 1 at the next phrase start`() {
        val single = config.copy(singleStepEaseBack = true)

        assertEquals(listOf(SpeedStep(16_000L, 1f)), EaseBack.steps(single, 0.97, grid, 16_000.0))
        assertEquals(listOf(SpeedStep(24_000L, 1f)), EaseBack.steps(single, 0.97, grid, 16_001.0))
    }
}
