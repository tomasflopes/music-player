package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.PcmBuffer
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads the 32-bit float WAV that ffmpeg writes to a pipe. The sizes in the header are unknown there (ffmpeg cannot
 * seek back to fill them), so the data chunk is taken to run to the end of the stream.
 */
internal object WavReader {
    private const val RIFF_HEADER_BYTES = 12
    private const val CHUNK_HEADER_BYTES = 8
    private const val ID_BYTES = 4
    private const val CHANNELS_OFFSET = 2
    private const val RATE_OFFSET = 4
    private const val BITS_OFFSET = 14
    private const val FORMAT_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE
    private const val FLOAT_BITS = 32
    private const val BYTES_PER_FLOAT = 4
    private const val UNSIGNED_SHORT = 0xFFFF
    private const val UNSIGNED_INT = 0xFFFFFFFFL

    private class Chunk(
        val bodyOffset: Int,
        val size: Long,
    )

    fun read(
        bytes: ByteArray,
        startMs: Long,
    ): PcmBuffer {
        if (bytes.size < RIFF_HEADER_BYTES || String(bytes, 0, ID_BYTES, Charsets.US_ASCII) != "RIFF") {
            fail("ffmpeg did not produce a WAV stream (${bytes.size} bytes)")
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val format = chunk(buffer, "fmt ") ?: fail("WAV stream has no fmt chunk")
        val data = chunk(buffer, "data") ?: fail("WAV stream has no data chunk")

        val tag = buffer.getShort(format.bodyOffset).toInt() and UNSIGNED_SHORT
        val channels = buffer.getShort(format.bodyOffset + CHANNELS_OFFSET).toInt()
        val sampleRate = buffer.getInt(format.bodyOffset + RATE_OFFSET)
        val bits = buffer.getShort(format.bodyOffset + BITS_OFFSET).toInt()
        if (!isSupported(tag, bits, channels)) {
            fail("Unexpected WAV format: tag $tag, $bits bits, $channels channels")
        }

        val available = bytes.size - data.bodyOffset
        val dataBytes = if (data.size > available) available else data.size.toInt()
        val samples = FloatArray(dataBytes / BYTES_PER_FLOAT / channels * channels)
        buffer.position(data.bodyOffset)
        buffer.asFloatBuffer().get(samples)
        return PcmBuffer(sampleRate, channels, startMs, samples)
    }

    private fun isSupported(
        tag: Int,
        bits: Int,
        channels: Int,
    ): Boolean {
        val floatTag = tag == FORMAT_FLOAT || tag == FORMAT_EXTENSIBLE
        return floatTag && bits == FLOAT_BITS && channels >= 1
    }

    private fun chunk(
        buffer: ByteBuffer,
        id: String,
    ): Chunk? {
        var offset = RIFF_HEADER_BYTES
        var found: Chunk? = null
        while (found == null && offset + CHUNK_HEADER_BYTES <= buffer.limit()) {
            val name = String(buffer.array(), offset, ID_BYTES, Charsets.US_ASCII)
            val size = buffer.getInt(offset + ID_BYTES).toLong() and UNSIGNED_INT
            val body = offset + CHUNK_HEADER_BYTES
            if (name == id) {
                found = Chunk(body, size)
            } else {
                offset = (body + size + (size and 1L)).coerceAtMost(buffer.limit().toLong()).toInt()
            }
        }
        return found
    }

    private fun fail(message: String): Nothing = throw PcmDecodeException(message)
}
