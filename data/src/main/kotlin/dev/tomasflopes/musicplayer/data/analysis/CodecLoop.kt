package dev.tomasflopes.musicplayer.data.analysis

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import dev.tomasflopes.musicplayer.core.analysis.PcmBuffer
import dev.tomasflopes.musicplayer.core.analysis.PcmChunkSink
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TIMEOUT_US = 10_000L
private const val US_PER_SECOND = 1_000_000L
private const val US_PER_MS = 1_000L
private const val SHORT_SCALE = 32_768f
private const val INITIAL_SAMPLES = 1 shl 20

/** A stream hands the sink about this many floats at a time (256 KiB), far under [PcmChunkSink.MAX_CHUNK_SAMPLES]. */
private const val STREAM_CHUNK_SAMPLES = 1 shl 16

/** A whole-file buffer is sized from the duration plus this margin; VBR durations are estimates, so it may grow. */
private const val SIZE_MARGIN = 1.05
private const val MAX_PRESIZED_SAMPLES = 1L shl 28

/** About ten seconds of nothing from the codec (10 ms per round) means it is stuck, not slow. */
private const val MAX_IDLE_ROUNDS = 1_000

/**
 * Feeds [extractor] into [codec] and collects the float PCM it returns, until the end of the file or until the output
 * reaches [endUs] (song time) when that is set. The buffer starts at the first frame the codec returns, which after a
 * seek can be a little before the one asked for. With a [sink] the output is not collected: [stream] hands it on in
 * chunks of about [STREAM_CHUNK_SAMPLES] floats, so memory does not grow with the song (ADR 0054).
 */
internal class CodecLoop(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    format: MediaFormat,
    private val endUs: Long?,
    private val sink: PcmChunkSink? = null,
) {
    private val info = MediaCodec.BufferInfo()
    private var samples = FloatArray(initialSamples(format, endUs, sink != null))
    private var size = 0
    private var inputDone = false
    private var outputDone = false
    private var idleRounds = 0
    private var firstPtsUs = -1L
    private var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    private var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    private var encoding = AudioFormat.ENCODING_PCM_16BIT
    private var beganRate = 0
    private var beganChannels = 0

    fun run(): PcmBuffer {
        pump()
        val startMs = firstPtsUs.coerceAtLeast(0L) / US_PER_MS
        return PcmBuffer(sampleRate, channels, startMs, samples.copyOf(size))
    }

    /** Decodes to the end into the sink given at construction. Throws when the file held no audio. */
    fun stream() {
        pump()
        flush()
        if (beganRate == 0) throw PcmDecodeException("No audio could be decoded")
    }

    private fun pump() {
        while (!outputDone) {
            feedInput()
            drainOutput()
            if (idleRounds > MAX_IDLE_ROUNDS) throw PcmDecodeException("The decoder stopped returning audio")
        }
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
        if (sink != null && size > 0 && size + extra > STREAM_CHUNK_SAMPLES) flush()
        if (size + extra > samples.size) samples = samples.copyOf(maxOf(samples.size * 2, size + extra))
    }

    /** Hands the collected samples to the sink and starts again from empty. The first call tells it the format. */
    private fun flush() {
        val out = sink ?: return
        if (size == 0) return
        if (beganRate == 0) {
            out.begin(sampleRate, channels)
            beganRate = sampleRate
            beganChannels = channels
        } else if (sampleRate != beganRate || channels != beganChannels) {
            throw PcmDecodeException("The audio format changed during the song")
        }
        out.accept(samples, size - size % channels)
        size = 0
    }

    private fun reachedEnd(): Boolean {
        val end = endUs
        if (end == null || firstPtsUs < 0L) return false
        val coveredUs = firstPtsUs + (size / channels) * US_PER_SECOND / sampleRate
        return coveredUs >= end
    }
}

/**
 * A whole-file decode knows its length from the format, so the buffer is sized once. Doubling from 1 M samples holds
 * the old and the new array at the same time, about 1.5 times the final size, which is what ran out of memory on long
 * songs (ADR 0054). A window decode stays small and keeps the default.
 */
private fun initialSamples(
    format: MediaFormat,
    endUs: Long?,
    streaming: Boolean,
): Int {
    val presized = !streaming && endUs == null && format.containsKey(MediaFormat.KEY_DURATION)
    if (!presized) return if (streaming) STREAM_CHUNK_SAMPLES else INITIAL_SAMPLES
    val seconds = format.getLong(MediaFormat.KEY_DURATION).toDouble() / US_PER_SECOND
    val perSecond = format.getInteger(MediaFormat.KEY_SAMPLE_RATE) * format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val expected = (seconds * perSecond * SIZE_MARGIN).toLong()
    return expected.coerceIn(INITIAL_SAMPLES.toLong(), MAX_PRESIZED_SAMPLES).toInt()
}
