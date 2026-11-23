package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos

/**
 * The talking-outro volumes (ADR 0104) for a 16 s overlap at 120 BPM: a 500 ms entry, a 2 s duck to -6 dB and a fade
 * over 4 bars (8 s), so the fade starts at 8 s.
 */
class TalkCurveTest {
    private val overlapMs = 16_000L
    private val curve = TalkCurve.of(overlapMs, beatMs = 500.0, barMs = 2_000.0, fadeBars = 4, duckDb = -6f)!!

    private fun at(ms: Long) = ms.toFloat() / overlapMs

    @Test
    fun `the incoming song rises over one beat and stays at full volume`() {
        assertEquals(0f, curve.incoming(0f))
        assertEquals(0.707f, curve.incoming(at(250)), 0.01f)
        assertEquals(1f, curve.incoming(at(500)))
        assertEquals(1f, curve.incoming(at(10_000)))
        assertEquals(1f, curve.incoming(1f))
    }

    @Test
    fun `the outgoing song ducks by six decibels over one bar and holds`() {
        val duck = 0.501f
        assertEquals(1f, curve.outgoing(0f))
        assertEquals(0.708f, curve.outgoing(at(1_000)), 0.005f)
        assertEquals(duck, curve.outgoing(at(2_000)), 0.002f)
        assertEquals(duck, curve.outgoing(at(5_000)), 0.002f)
        assertEquals(duck, curve.outgoing(at(8_000)), 0.002f)
    }

    @Test
    fun `the outgoing song fades out over the last four bars`() {
        val duck = 0.501f
        assertEquals(duck * cos(0.5 * PI / 2).toFloat(), curve.outgoing(at(12_000)), 0.003f)
        assertTrue(curve.outgoing(at(15_000)) < 0.1f)
        assertEquals(0f, curve.outgoing(1f))
    }

    @Test
    fun `both volumes stay in range and only move one way`() {
        var out = 1f
        var inc = 0f
        for (i in 0..1_000) {
            val p = i / 1_000f
            assertTrue(curve.outgoing(p) in 0f..1f && curve.incoming(p) in 0f..1f)
            assertTrue(curve.outgoing(p) <= out && curve.incoming(p) >= inc, "moved the wrong way at $p")
            out = curve.outgoing(p)
            inc = curve.incoming(p)
        }
    }

    @Test
    fun `a progress that is not a number is the start`() {
        assertEquals(1f, curve.outgoing(Float.NaN))
        assertEquals(0f, curve.incoming(Float.NaN))
    }

    @Test
    fun `the parts are shortened to fit a short overlap`() {
        val short = TalkCurve.of(6_000, beatMs = 500.0, barMs = 2_000.0, fadeBars = 4, duckDb = -6f)!!

        assertEquals(0.501f, short.outgoing(1_500f / 6_000f), 0.002f)
        assertEquals(0f, short.outgoing(1f))
        assertEquals(1f, short.incoming(1f))
    }

    @Test
    fun `an overlap too short for a fade, or a bad length, has no curve`() {
        assertNull(TalkCurve.of(1_000, 500.0, 2_000.0, 4, -6f))
        assertNull(TalkCurve.of(0, 500.0, 2_000.0, 4, -6f))
        assertNull(TalkCurve.of(10_000, 0.0, 2_000.0, 4, -6f))
        assertNotNull(TalkCurve.of(4_000, 500.0, 2_000.0, 4, -6f))
    }
}
