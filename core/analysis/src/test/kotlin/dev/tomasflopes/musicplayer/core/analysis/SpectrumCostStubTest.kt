package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.core.analysis.config.SpectrumConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

class SpectrumCostStubTest {
    @Test
    fun `the fft puts a cosine in its own bin`() {
        val size = 1_024
        val re = DoubleArray(size) { cos(2.0 * PI * BIN * it / size) }
        val im = DoubleArray(size)

        Fft(size).transform(re, im)

        assertEquals(size / 2.0, hypot(re[BIN], im[BIN]), 1e-6)
        assertEquals(0.0, hypot(re[BIN + 5], im[BIN + 5]), 1e-6)
    }

    @Test
    fun `the fft refuses a size that is not a power of two`() {
        assertThrows(IllegalArgumentException::class.java) { Fft(1_000) }
    }

    @Test
    fun `frames are counted from the hop`() {
        val stub = SpectrumCostStub(SpectrumConfig.LARGE)

        assertEquals(0, stub.frameCount(2_047))
        assertEquals(1, stub.frameCount(2_048))
        assertEquals(3, stub.frameCount(2_048 + 2 * 512))
    }

    @Test
    fun `a signal costs something and the same signal costs the same sum`() {
        val signal = FloatArray(44_100) { cos(2.0 * PI * 440.0 * it / 44_100).toFloat() }
        val stub = SpectrumCostStub(SpectrumConfig.SMALL)

        val first = stub.run(signal)

        assertTrue(first > 0.0)
        assertEquals(first, stub.run(signal))
    }

    private companion object {
        const val BIN = 10
    }
}
