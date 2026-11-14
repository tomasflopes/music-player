package dev.tomasflopes.musicplayer.data.analysis

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import dev.tomasflopes.musicplayer.core.analysis.PcmBuffer
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TIMEOUT_US = 10_000L
private const val US_PER_SECOND = 1_000_000L
private const val US_PER_MS = 1_000L
private const val SHORT_SCALE = 32_768f
private const val INITIAL_SAMPLES = 1 shl 20

/** About ten seconds of nothing from the codec (10 ms per round) means it is stuck, not slow. */
private const val MAX_IDLE_ROUNDS = 1_000

/**
 * Feeds [extractor] into [codec] and collects the float PCM it returns, until the end of the file or until the output
 * reaches [endUs] (song time) when that is set. The buffer starts at the first frame the codec returns, which after a
 * seek can be a little before the one asked for.
 */
internal class CodecLoop(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    format: MediaFormat,
    private val endUs: Long?,
) {
    private val info = MediaCodec.BufferInfo()
    private var samples = FloatArray(INITIAL_SAMPLES)
    private var size = 0
    private var inputDone = false
    private var outputDone = false
    private var idleRounds = 0
    private var firstPtsUs = -1L
    private var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    private var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    private var encoding = AudioFormat.ENCODING_PCM_16BIT

    fun run(): PcmBuffer {
        while (!outputDone) {
            feedInput()
            drainOutput()
            if (idleRounds > MAX_IDLE_ROUNDS) throw PcmDecodeException("The decoder stopped returning audio")
        }
        val startMs = firstPtsUs.coerceAtLeast(0L) / US_PER_MS
        return PcmBuffer(sampleRate, channels, startMs, samples.copyOf(size))
    }

    private fun feedInput() {
        if (inputDone) return
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        val input = if (index >= 0) codec.getInputBuffer(index) else null
        if (input != null) {
            val read = extractor.readSampleData(input, 0)
            if (read < 0) {
                codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
            } else {
                codec.queueInputBuffer(index, 0, read, extractor.sampleTime, 0)
                extractor.advance()
            }
        }
    }

    private fun drainOutput() {
        val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        when {
            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> readFormat(codec.outputFormat)
            index >= 0 -> {
                idleRounds = 0
                consume(index)
                codec.releaseOutputBuffer(index, false)
            }
            else -> idleRounds += 1
        }
    }

    private fun readFormat(format: MediaFormat) {
        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        encoding =
            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
    }

    private fun consume(index: Int) {
        val buffer = codec.getOutputBuffer(index)
        val isData = info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
        if (buffer != null && isData) append(buffer)
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || reachedEnd()) outputDone = true
    }

    private fun append(buffer: ByteBuffer) {
        if (firstPtsUs < 0L) firstPtsUs = info.presentationTimeUs
        buffer.order(ByteOrder.nativeOrder())
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val floats = buffer.asFloatBuffer()
            val count = floats.remaining()
            ensureRoom(count)
            floats.get(samples, size, count)
            size += count
        } else {
            val shorts = buffer.asShortBuffer()
            ensureRoom(shorts.remaining())
            while (shorts.hasRemaining()) {
                samples[size] = shorts.get() / SHORT_SCALE
                size += 1
            }
        }
    }

    private fun ensureRoom(extra: Int) {
        if (size + extra > samples.size) samples = samples.copyOf(maxOf(samples.size * 2, size + extra))
    }

    private fun reachedEnd(): Boolean {
        val end = endUs
        if (end == null || firstPtsUs < 0L) return false
        val coveredUs = firstPtsUs + (size / channels) * US_PER_SECOND / sampleRate
        return coveredUs >= end
    }
}
