package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EqualPowerTailTest {
    @Test
    fun `it starts at the gains it was given and ends at the equal power end`() {
        val tail = EqualPowerTail(0.6f, 1f, 0.8f)

        assertEquals(0.8f, tail.outgoing(0.6f), 1e-6f)
        assertEquals(1f, tail.incoming(0.6f), 1e-6f)
        assertEquals(0f, tail.outgoing(1f), 1e-6f)
        assertEquals(1f, tail.incoming(1f), 1e-6f)
    }

    @Test
    fun `an incoming song that was still rising keeps rising to full`() {
        val tail = EqualPowerTail(0.2f, 0.5f, 1f)

        var previous = tail.incoming(0.2f)
        assertEquals(0.5f, previous, 1e-6f)
        for (i in 1..20) {
            val value = tail.incoming(0.2f + 0.8f * i / 20)
            assertTrue(value >= previous)
            previous = value
        }
        assertEquals(1f, previous, 1e-6f)
    }

    @Test
    fun `it stays inside zero and one and survives NaN and a start at the end`() {
        val tail = EqualPowerTail(1f, 0.3f, 0.7f)

        assertEquals(0f, tail.outgoing(Float.NaN), 1e-6f)
        assertTrue(tail.incoming(0.5f) in 0f..1f)
    }
}
