package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.PcmChunkSink
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads the 32-bit float WAV that ffmpeg writes to a pipe as a stream: the header first, then the data in chunks, so
 * the song never sits in memory. Like [WavReader], the data chunk is taken to run to the end of the stream.
 */
internal object WavStreamReader {
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
    private const val CHUNK_SAMPLES = 1 shl 16

    private class Format(
        val sampleRate: Int,
        val channels: Int,
    )

    fun stream(
        input: InputStream,
        sink: PcmChunkSink,
    ) {
        val riff = input.readNBytes(RIFF_HEADER_BYTES)
        if (riff.size < RIFF_HEADER_BYTES || String(riff, 0, ID_BYTES, Charsets.US_ASCII) != "RIFF") {
            fail("ffmpeg did not produce a WAV stream")
        }
        val format = readHeader(input)
        val chunkSamples = CHUNK_SAMPLES / format.channels * format.channels
        val bytes = ByteArray(chunkSamples * BYTES_PER_FLOAT)
        val samples = FloatArray(chunkSamples)
        var began = false
        var more = true
        while (more) {
            val read = input.readNBytes(bytes, 0, bytes.size)
            val count = read / BYTES_PER_FLOAT / format.channels * format.channels
            if (count > 0) {
                if (!began) sink.begin(format.sampleRate, format.channels)
                began = true
                val buffer = ByteBuffer.wrap(bytes, 0, count * BYTES_PER_FLOAT).order(ByteOrder.LITTLE_ENDIAN)
                buffer.asFloatBuffer().get(samples, 0, count)
                sink.accept(samples, count)
            }
            // A short read is the end of the stream.
            more = read == bytes.size
        }
        if (!began) fail("ffmpeg decoded no audio")
    }

    /** Reads chunks up to the data chunk; returns the format from `fmt `. Leaves the stream at the first sample. */
    private fun readHeader(input: InputStream): Format {
        var format: Format? = null
        while (true) {
            val header = input.readNBytes(CHUNK_HEADER_BYTES)
            if (header.size < CHUNK_HEADER_BYTES) fail("WAV stream has no data chunk")
            val id = String(header, 0, ID_BYTES, Charsets.US_ASCII)
            val sizeBytes = ByteBuffer.wrap(header, ID_BYTES, ID_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            val size = sizeBytes.int.toLong() and UNSIGNED_INT
            when (id) {
                "data" -> return format ?: fail("WAV stream has no fmt chunk")
                "fmt " -> format = parseFormat(input.readNBytes(size.toInt() + (size.toInt() and 1)))
                else -> input.skipNBytes(size + (size and 1L))
            }
        }
    }

    private fun parseFormat(body: ByteArray): Format {
        val buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
        if (body.size < BITS_OFFSET + 2) fail("WAV fmt chunk is too short")
        val tag = buffer.getShort(0).toInt() and UNSIGNED_SHORT
        val channels = buffer.getShort(CHANNELS_OFFSET).toInt()
        val sampleRate = buffer.getInt(RATE_OFFSET)
        val bits = buffer.getShort(BITS_OFFSET).toInt()
        val floatTag = tag == FORMAT_FLOAT || tag == FORMAT_EXTENSIBLE
        if (!floatTag || bits != FLOAT_BITS || channels < 1) {
            fail("Unexpected WAV format: tag $tag, $bits bits, $channels channels")
        }
        return Format(sampleRate, channels)
    }

    private fun fail(message: String): Nothing = throw PcmDecodeException(message)
}
