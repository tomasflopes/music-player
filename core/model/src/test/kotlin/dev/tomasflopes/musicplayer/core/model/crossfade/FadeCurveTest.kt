package dev.tomasflopes.musicplayer.core.model.crossfade

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FadeCurveTest {
    @Test
    fun `starts with the outgoing track at full volume and the incoming one silent`() {
        assertEquals(FadeGains(outgoing = 1f, incoming = 0f), FadeCurve.gains(0f))
    }

    @Test
    fun `ends with the outgoing track silent and the incoming one at full volume`() {
        assertEquals(FadeGains(outgoing = 0f, incoming = 1f), FadeCurve.gains(1f))
    }

    @Test
    fun `clamps progress outside zero to one`() {
        assertEquals(FadeCurve.gains(0f), FadeCurve.gains(-3f))
        assertEquals(FadeCurve.gains(1f), FadeCurve.gains(7f))
        assertEquals(FadeCurve.gains(0f), FadeCurve.gains(Float.NaN))
    }

    @Test
    fun `the outgoing gain never rises and the incoming gain never falls`() {
        var previous = FadeCurve.gains(0f)
        for (step in 1..1_000) {
            val now = FadeCurve.gains(step / 1_000f)
            assertTrue(now.outgoing <= previous.outgoing, "outgoing rose at step $step")
            assertTrue(now.incoming >= previous.incoming, "incoming fell at step $step")
            previous = now
        }
    }

    @Test
    fun `power is constant all the way through`() {
        for (step in 0..100) {
            val gains = FadeCurve.gains(step / 100f)
            val power = gains.outgoing * gains.outgoing + gains.incoming * gains.incoming
            assertEquals(1f, power, 1e-5f, "power at step $step")
        }
    }

    @Test
    fun `both tracks are at about 71 percent in the middle`() {
        val middle = FadeCurve.gains(0.5f)

        assertEquals(0.7071f, middle.outgoing, 1e-3f)
        assertEquals(0.7071f, middle.incoming, 1e-3f)
    }
}
