package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class DjVolumeCurveTest {
    @Test
    fun `the incoming rises over the first half and the outgoing holds`() {
        assertEquals(0f, DjVolumeCurve.incoming(0f))
        assertEquals(1f, DjVolumeCurve.incoming(0.5f))
        assertEquals(1f, DjVolumeCurve.outgoing(0.5f))
        assertEquals(1f, DjVolumeCurve.outgoing(0.25f))
        assertEquals(sqrt(0.5f), DjVolumeCurve.incoming(0.25f), 1e-5f)
    }

    @Test
    fun `the outgoing falls over the second half and the incoming holds`() {
        assertEquals(1f, DjVolumeCurve.incoming(0.75f))
        assertEquals(sqrt(0.5f), DjVolumeCurve.outgoing(0.75f), 1e-5f)
        assertEquals(0f, DjVolumeCurve.outgoing(1f))
    }

    @Test
    fun `progress outside 0 to 1 and NaN are clamped`() {
        assertEquals(1f, DjVolumeCurve.outgoing(-1f))
        assertEquals(0f, DjVolumeCurve.incoming(-1f))
        assertEquals(0f, DjVolumeCurve.outgoing(2f))
        assertEquals(1f, DjVolumeCurve.incoming(2f))
        assertEquals(1f, DjVolumeCurve.outgoing(Float.NaN))
    }

    @Test
    fun `the equal-power curve is the phase 6 curve`() {
        assertEquals(1f, EqualPowerCurve.outgoing(0f))
        assertEquals(1f, EqualPowerCurve.incoming(1f))
        assertEquals(sqrt(0.5f), EqualPowerCurve.incoming(0.5f), 1e-5f)
    }
}
