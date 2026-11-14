package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavReaderTest {
    /** The header ffmpeg writes to a pipe: sizes are 0xFFFFFFFF, and a LIST chunk may sit before the data. */
    private fun wav(
        channels: Int,
        rate: Int,
        samples: FloatArray,
        withList: Boolean = false,
    ): ByteArray {
        val list = if (withList) byteArrayOf(*"LIST".toByteArray(), 4, 0, 0, 0, 1, 2, 3, 4) else ByteArray(0)
        val buffer = ByteBuffer.allocate(12 + 24 + list.size + 8 + samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(-1).put("WAVE".toByteArray())
        buffer
            .put("fmt ".toByteArray())
            .putInt(16)
            .putShort(3)
            .putShort(channels.toShort())
            .putInt(rate)
        buffer.putInt(rate * channels * 4).putShort((channels * 4).toShort()).putShort(32)
        buffer.put(list)
        buffer.put("data".toByteArray()).putInt(-1)
        samples.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @Test
    fun `float samples, rate and channels come out as written`() {
        val pcm = WavReader.read(wav(2, 48_000, floatArrayOf(0.25f, -0.5f, 1f, 0f)), startMs = 1_500L)

        assertEquals(48_000, pcm.sampleRate)
        assertEquals(2, pcm.channels)
        assertEquals(1_500L, pcm.startMs)
        assertArrayEquals(floatArrayOf(0.25f, -0.5f, 1f, 0f), pcm.samples)
    }

    @Test
    fun `a chunk before the data is skipped`() {
        val pcm = WavReader.read(wav(1, 44_100, floatArrayOf(0.5f, 0.25f), withList = true), startMs = 0L)

        assertArrayEquals(floatArrayOf(0.5f, 0.25f), pcm.samples)
    }

    @Test
    fun `a stream that is not a wav is a decode failure`() {
        assertThrows(PcmDecodeException::class.java) { WavReader.read("not audio at all".toByteArray(), startMs = 0L) }
    }
}
