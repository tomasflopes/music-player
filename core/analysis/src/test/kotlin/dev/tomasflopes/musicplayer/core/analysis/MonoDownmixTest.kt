package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MonoDownmixTest {
    @Test
    fun `stereo is the average of the two channels`() {
        val samples = floatArrayOf(1f, 0f, 0.5f, 0.5f, -1f, 1f)
        val pcm = PcmBuffer(sampleRate = 1_000, channels = 2, startMs = 0L, samples = samples)

        val mono = MonoDownmix.toMono(pcm)

        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0f), mono.samples)
    }

    @Test
    fun `mono is left as it is`() {
        val pcm = PcmBuffer(sampleRate = 1_000, channels = 1, startMs = 250L, samples = floatArrayOf(0.1f, 0.2f))

        val mono = MonoDownmix.toMono(pcm)

        assertArrayEquals(floatArrayOf(0.1f, 0.2f), mono.samples)
        assertEquals(250L, mono.startMs)
    }

    @Test
    fun `a window is cut in song time, whatever time the buffer starts at`() {
        val samples = FloatArray(1_000) { it.toFloat() }
        val mono = MonoPcm(sampleRate = 1_000, startMs = 500L, samples = samples)

        val window = mono.window(fromMs = 700L, lengthMs = 100L)

        assertEquals(100, window.size)
        assertEquals(200f, window.first())
    }

    @Test
    fun `a window is clipped to what the buffer holds`() {
        val mono = MonoPcm(sampleRate = 1_000, startMs = 500L, samples = FloatArray(100))

        assertEquals(100, mono.window(fromMs = 0L, lengthMs = 10_000L).size)
        assertEquals(0, mono.window(fromMs = 9_000L, lengthMs = 100L).size)
    }
}
