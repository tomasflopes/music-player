package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TempoMatcherTest {
    private fun best(
        outgoing: Double,
        incoming: Double,
        max: Double = 0.08,
    ) = TempoMatcher.best(outgoing, incoming, max)

    @Test
    fun `equal tempos read as same tempo with no stretch`() {
        val match = best(120.0, 120.0)!!

        assertEquals(1.0, match.multiplier)
        assertEquals(1.0, match.ratio, 1e-12)
    }

    @Test
    fun `ratio is the outgoing tempo over the incoming tempo as read`() {
        assertEquals(120.0 / 123.0, best(120.0, 123.0)!!.ratio, 1e-12)
        assertEquals(1.04, best(104.0, 100.0)!!.ratio, 1e-12)
    }

    @Test
    fun `half and double readings win when the plain one is too far`() {
        assertEquals(2.0, best(120.0, 60.0)!!.multiplier)
        assertEquals(0.5, best(120.0, 240.0)!!.multiplier)
        assertEquals(1.0, best(70.0, 140.0, 0.0)?.ratio ?: 1.0, 1e-12)
    }

    @Test
    fun `the smallest stretch wins even when another reading is also within the limit`() {
        val match = best(100.0, 202.0)!!

        assertEquals(0.5, match.multiplier)
        assertEquals(100.0 / 101.0, match.ratio, 1e-12)
    }

    @Test
    fun `the limit is inclusive and a bit over it is refused`() {
        assertNotNull(best(108.0, 100.0))
        assertNotNull(best(92.0, 100.0))
        assertNull(best(108.5, 100.0))
        assertNull(best(91.0, 100.0))
    }

    @Test
    fun `a tempo that is not a number or not positive matches nothing`() {
        assertNull(best(Double.NaN, 120.0))
        assertNull(best(120.0, 0.0))
        assertNull(best(-1.0, 120.0))
    }
}
