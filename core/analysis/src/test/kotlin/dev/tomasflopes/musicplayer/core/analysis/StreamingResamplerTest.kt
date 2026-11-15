package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class StreamingResamplerTest {
    private fun resample(
        input: FloatArray,
        inRate: Int,
        chunk: Int,
    ): FloatArray {
        val resampler = StreamingResampler(inRate, RATE)
        val out = FloatSink()
        var from = 0
        while (from < input.size) {
            val count = minOf(chunk, input.size - from)
            out.addAll(resampler.push(input.copyOfRange(from, from + count), count))
            from += count
        }
        out.addAll(resampler.finish())
        return out.toArray()
    }

    @Test
    fun `equal rates pass the samples through`() {
        val input = TestSignals.sine(440.0, 1.0)

        assertArrayEquals(input, resample(input, RATE, 1_000))
    }

    @Test
    fun `the output length follows the ratio`() {
        assertEquals(RATE * 3, resample(TestSignals.sine(440.0, 3.0, 44_100), 44_100, 4_096).size)
        assertEquals(RATE * 3, resample(TestSignals.sine(440.0, 3.0, 48_000), 48_000, 4_096).size)
    }

    @Test
    fun `the chunk size does not change a single sample`() {
        val input = TestSignals.sine(440.0, 2.0, 48_000)

        assertArrayEquals(resample(input, 48_000, 48_000 * 2), resample(input, 48_000, 313))
    }

    @Test
    fun `a tone below the new Nyquist keeps its level and a tone above it is removed`() {
        val kept = resample(TestSignals.sine(1_000.0, 2.0, 48_000), 48_000, 4_096)
        val removed = resample(TestSignals.sine(15_000.0, 2.0, 48_000), 48_000, 4_096)

        assertEquals(0.5 / sqrt(2.0), rms(kept), 0.01)
        assertEquals(0.0, rms(removed), 0.01)
    }

    @Test
    fun `no sample is lost or invented at the edges`() {
        val out = resample(FloatArray(10_000) { 0.25f }, 44_100, 1_000)

        assertEquals(0.25f, out[out.size / 2], 0.001f)
        assertEquals(true, out.all { abs(it) <= 0.3f })
    }

    private fun rms(samples: FloatArray): Double {
        val middle = samples.copyOfRange(samples.size / 4, samples.size * 3 / 4)
        return sqrt(middle.sumOf { it.toDouble() * it } / middle.size)
    }
}
