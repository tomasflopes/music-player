package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 120 BPM on both grids with the first beat at 0: a beat is 500 ms, a bar 2 s; the lock ticks every 250 ms. */
class PhaseLockTest {
    private val config = TransitionConfig(phaseLockEnabled = true)
    private val grid = BeatGrid(0.0, 120.0, 4, 16)

    private fun lock(planned: Double = 1.0) = PhaseLock(config, grid, grid, planned)

    private fun speedOf(action: PhaseLock.Action) = (action as PhaseLock.Action.Speed).value

    @Test
    fun `aligned songs keep the planned speed`() {
        assertEquals(1f, speedOf(lock().update(10_000.0, 10_000.0, 1.0)))
        assertEquals(0.97f, speedOf(lock(0.97).update(10_000.0, 10_000.0, 0.97)), 1e-6f)
    }

    @Test
    fun `an incoming song that is ahead slows down and one that is behind speeds up`() {
        val ahead = speedOf(lock().update(10_000.0, 10_010.0, 1.0))
        val behind = speedOf(lock().update(10_000.0, 9_990.0, 1.0))

        assertTrue(ahead < 1f, "ahead $ahead")
        assertTrue(behind > 1f, "behind $behind")
    }

    @Test
    fun `the nudge never leaves the maximum around the planned speed`() {
        val far = speedOf(lock(1.04).update(10_000.0, 10_200.0, 1.04))
        val farBehind = speedOf(lock(1.04).update(10_000.0, 9_800.0, 1.04))

        assertEquals(1.04f - config.maxNudge.toFloat(), far, 1e-6f)
        assertEquals(1.04f + config.maxNudge.toFloat(), farBehind, 1e-6f)
    }

    @Test
    fun `an error above the limit for two bars abandons the match`() {
        val lock = lock()
        val ticks = (2 * 2_000 / config.phaseLockTickMs).toInt()

        repeat(ticks - 1) { assertInstanceOf(PhaseLock.Action.Speed::class.java, lock.update(0.0, 100.0, 1.0)) }

        assertEquals(PhaseLock.Action.Abandon, lock.update(0.0, 100.0, 1.0))
    }

    @Test
    fun `an error that comes back under the limit resets the count`() {
        val lock = lock()
        val ticks = (2 * 2_000 / config.phaseLockTickMs).toInt()

        repeat(ticks - 1) { lock.update(0.0, 100.0, 1.0) }
        lock.update(0.0, 10.0, 1.0)
        repeat(ticks - 1) { assertInstanceOf(PhaseLock.Action.Speed::class.java, lock.update(0.0, 100.0, 1.0)) }
    }

    @Test
    fun `the error is read in real time when the incoming song is stretched`() {
        val stretched = BeatGrid(0.0, 100.0, 4, 16)
        val lock = PhaseLock(config, grid, stretched, 1.2)

        // 12 ms into an incoming beat of the file are 10 ms of real time at speed 1.2; the outgoing beat is on 0.
        assertEquals(1.2f - 0.0025f, speedOf(lock.update(10_000.0, 12_012.0, 1.2)), 1e-5f)
    }
}
