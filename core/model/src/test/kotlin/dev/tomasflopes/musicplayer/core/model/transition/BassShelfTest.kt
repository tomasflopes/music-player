package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/** The smoothed bass shelf (design phase 7, slice 3, ADR 0101): dry/wet mix of a fixed full-cut shelf. */
class BassShelfTest {
    private val sampleRate = 44_100

    private fun shelf(channels: Int = 1) = BassShelf(sampleRate, channels)

    private fun tone(
        hz: Double,
        frames: Int,
        from: Int = 0,
        amplitude: Double = 0.5,
    ) = FloatArray(frames) { (amplitude * sin(2 * PI * hz * (from + it) / sampleRate)).toFloat() }

    private fun peakDb(
        samples: FloatArray,
        reference: Double = 0.5,
    ) = 20 * log10(samples.maxOf { abs(it) } / reference)

    @Test
    fun `a shelf that was never asked to cut leaves the samples bit for bit and reports pass through`() {
        val shelf = shelf()
        val samples = tone(60.0, 4_410)
        val before = samples.copyOf()

        shelf.process(samples, samples.size)

        assertTrue(shelf.isPassThrough)
        assertArrayEquals(before, samples)
    }

    @Test
    fun `at the full cut a 50 Hz tone is about 23 dB down and a 1 kHz tone is unchanged`() {
        val low = shelf().apply { snapToDb(-24f) }
        val lowSamples = tone(50.0, 44_100)
        low.process(lowSamples, lowSamples.size)
        assertEquals(-23.2, peakDb(lowSamples.copyOfRange(22_050, 44_100)), 0.5)

        val high = shelf().apply { snapToDb(-24f) }
        val highSamples = tone(1_000.0, 44_100)
        high.process(highSamples, highSamples.size)
        assertEquals(0.0, peakDb(highSamples.copyOfRange(22_050, 44_100)), 0.15)
    }

    @Test
    fun `half way down the 50 Hz tone is about half the cut in amplitude terms`() {
        val shelf = shelf().apply { snapToDb(-12f) }
        val samples = tone(50.0, 44_100)

        shelf.process(samples, samples.size)

        assertEquals(-12.0, peakDb(samples.copyOfRange(22_050, 44_100)), 1.0)
    }

    @Test
    fun `no step is larger than the tone's own slope when the cut is released or applied`() {
        for (hz in listOf(40.0, 60.0, 100.0, 150.0)) {
            for ((start, end) in listOf(-24f to 0f, 0f to -24f)) {
                val shelf = shelf().apply { snapToDb(start) }
                val all = tone(hz, 8_000)
                // Warm the filter, then change the target and keep going in blocks the size of a typical buffer.
                shelf.process(all, 3_000)
                shelf.setTargetDb(end)
                var at = 3_000
                while (at < all.size) {
                    val frames = minOf(512, all.size - at)
                    val block = all.copyOfRange(at, at + frames)
                    shelf.process(block, frames)
                    block.copyInto(all, at)
                    at += frames
                }
                val slope = 0.5 * 2 * PI * hz / sampleRate
                val largest = (1 until all.size).maxOf { abs(all[it] - all[it - 1]) }
                assertTrue(largest <= slope * 1.05 + 1e-4, "$hz Hz $start to $end: step $largest, slope $slope")
            }
        }
    }

    @Test
    fun `bypass ramps to flat and then the samples are bit for bit again`() {
        val shelf = shelf().apply { snapToDb(-24f) }
        shelf.process(tone(60.0, 2_000), 2_000)

        shelf.bypass()
        assertFalse(shelf.isPassThrough)
        shelf.process(tone(60.0, 4_000), 4_000)

        assertTrue(shelf.isPassThrough)
        val samples = tone(60.0, 4_410)
        val before = samples.copyOf()
        shelf.process(samples, samples.size)
        assertArrayEquals(before, samples)
    }

    @Test
    fun `the ramp takes about 20 ms`() {
        val shelf = shelf().apply { snapToDb(-24f) }
        shelf.bypass()

        shelf.process(FloatArray(800), 800)
        assertFalse(shelf.isPassThrough)
        shelf.process(FloatArray(200), 200)
        assertTrue(shelf.isPassThrough)
    }

    @Test
    fun `NaN is ignored and a gain above 0 or below the depth is clamped`() {
        val shelf = shelf().apply { snapToDb(-24f) }

        shelf.setTargetDb(Float.NaN)
        assertEquals(-24f, shelf.targetDb)
        shelf.setTargetDb(6f)
        assertEquals(0f, shelf.targetDb)
        shelf.setTargetDb(-60f)
        assertEquals(-24f, shelf.targetDb)
    }

    @Test
    fun `channels are filtered on their own`() {
        val shelf = shelf(channels = 2).apply { snapToDb(-24f) }
        val mono = tone(50.0, 4_000)
        val stereo = FloatArray(8_000) { if (it % 2 == 0) mono[it / 2] else 0f }

        shelf.process(stereo, 4_000)

        assertTrue((1 until 8_000 step 2).all { stereo[it] == 0f })
        assertTrue((0 until 8_000 step 2).any { stereo[it] != 0f })
    }

    @Test
    fun `the output is never NaN or infinite at full scale`() {
        val shelf = shelf().apply { snapToDb(-24f) }
        val samples = FloatArray(20_000) { if (it % 2 == 0) 1f else -1f }

        shelf.process(samples, samples.size)

        assertTrue(samples.none { it.isNaN() || it.isInfinite() })
    }
}
