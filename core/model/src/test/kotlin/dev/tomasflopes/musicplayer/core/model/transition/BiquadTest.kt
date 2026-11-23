package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random

/** The low shelf of the bass swap (design phase 7, slice 3): response, flat state and the run through samples. */
class BiquadTest {
    private val sampleRate = 44_100

    private fun cut(
        gainDb: Double = -24.0,
        hz: Double = 150.0,
    ) = BiquadCoefficients.lowShelf(sampleRate, hz, gainDb)

    @Test
    fun `a 24 dB cut shelf at 150 Hz is nearly full cut at 50 Hz`() {
        assertEquals(-23.2, cut().magnitudeDb(50.0, sampleRate), 0.3)
    }

    @Test
    fun `the corner of the shelf sits half way, 12 dB down at 150 Hz`() {
        assertEquals(-12.0, cut().magnitudeDb(150.0, sampleRate), 0.1)
    }

    @Test
    fun `a 1 kHz tone is unchanged by the cut`() {
        assertEquals(0.0, cut().magnitudeDb(1_000.0, sampleRate), 0.1)
    }

    @Test
    fun `a gain of 0 dB is flat at every frequency`() {
        val flat = cut(gainDb = 0.0)
        listOf(20.0, 50.0, 150.0, 1_000.0, 10_000.0).forEach {
            assertEquals(0.0, flat.magnitudeDb(it, sampleRate), 1e-9)
        }
    }

    @Test
    fun `a sine through the filter comes out at the response the coefficients give`() {
        for (hz in listOf(50.0, 150.0, 1_000.0)) {
            val coefficients = cut()
            val biquad = Biquad(coefficients)
            val samples = 44_100
            var peak = 0.0
            for (i in 0 until samples) {
                val out = biquad.process(sin(2 * PI * hz * i / sampleRate).toFloat())
                if (i > samples / 2) peak = maxOf(peak, abs(out.toDouble()))
            }
            assertEquals(coefficients.magnitudeDb(hz, sampleRate), 20 * log10(peak), 0.1)
        }
    }

    @Test
    fun `the pass through coefficients return every sample bit for bit`() {
        val biquad = Biquad(BiquadCoefficients.PASS_THROUGH)
        val random = Random(7)
        repeat(10_000) {
            val x = random.nextFloat() * 2f - 1f
            assertEquals(x.toRawBits(), biquad.process(x).toRawBits())
        }
    }

    @Test
    fun `the output is never NaN or infinite for full scale input and steep gains`() {
        val biquad = Biquad(cut(gainDb = -60.0))
        repeat(50_000) {
            val out = biquad.process(if (it % 2 == 0) 1f else -1f)
            assertFalse(out.isNaN() || out.isInfinite())
        }
    }

    @Test
    fun `changing the coefficients keeps the state, so the output has no jump`() {
        val biquad = Biquad(cut(gainDb = -24.0))
        var last = 0f
        for (i in 0 until 2_000) last = biquad.process(sin(2 * PI * 60.0 * i / sampleRate).toFloat())
        biquad.coefficients = cut(gainDb = -23.5)
        val next = biquad.process(sin(2 * PI * 60.0 * 2_000 / sampleRate).toFloat())

        assertEquals(last.toDouble(), next.toDouble(), 0.05)
    }

    @Test
    fun `reset clears the state`() {
        val biquad = Biquad(cut())
        repeat(100) { biquad.process(1f) }
        biquad.reset()
        val fresh = Biquad(cut())

        assertEquals(fresh.process(0.5f), biquad.process(0.5f))
    }
}
