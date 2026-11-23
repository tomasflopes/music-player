package dev.tomasflopes.musicplayer.playback.bass

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import dev.tomasflopes.musicplayer.core.model.transition.TransitionConfig
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random

/** The Media3 processor around the bass shelf (ADR 0101, 0102): bypass is byte for byte, the cut is in numbers. */
class ShelfAudioProcessorTest {
    /** The numbers of slice 3 (ADR 0100); the filter is tested at them, whatever the defaults are. */
    private val slice3 = TransitionConfig(bassCutHz = 150, bassCutDb = -24f)

    private val rate = 44_100

    private fun processor(
        encoding: Int = C.ENCODING_PCM_16BIT,
        channels: Int = 2,
    ) = ShelfAudioProcessor(slice3).also {
        it.configure(AudioFormat(rate, channels, encoding))
        it.flush(AudioProcessor.StreamMetadata.DEFAULT)
    }

    private fun run(
        processor: ShelfAudioProcessor,
        input: ByteBuffer,
    ): ByteArray {
        processor.queueInput(input)
        val out = processor.output
        return ByteArray(out.remaining()).also(out::get)
    }

    private fun shorts(values: ShortArray): ByteBuffer =
        ByteBuffer.allocate(values.size * 2).order(ByteOrder.nativeOrder()).also {
            values.forEach(it::putShort)
            it.flip()
        }

    private fun tone16(
        hz: Double,
        frames: Int,
    ): ShortArray = ShortArray(frames * 2) { (16_384 * sin(2 * PI * hz * (it / 2) / rate)).toInt().toShort() }

    private fun peakDb16(bytes: ByteArray): Double {
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer()
        var peak = 0
        for (i in shorts.limit() / 2 until shorts.limit()) peak = maxOf(peak, abs(shorts.get(i).toInt()))
        return 20 * log10(peak / 16_384.0)
    }

    @Test
    fun `a flat shelf copies 16 bit samples byte for byte`() {
        val processor = processor()
        val random = Random(3)
        val input = ShortArray(8_820) { random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }

        val out = run(processor, shorts(input))

        val expected = ByteArray(input.size * 2).also { shorts(input).get(it) }
        assertArrayEquals(expected, out)
    }

    @Test
    fun `a flat shelf copies float samples byte for byte`() {
        val processor = processor(encoding = C.ENCODING_PCM_FLOAT)
        val random = Random(4)
        val buffer = ByteBuffer.allocate(4_000 * 4).order(ByteOrder.nativeOrder())
        repeat(4_000) { buffer.putFloat(random.nextFloat() * 2f - 1f) }
        buffer.flip()
        val expected = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }

        val out = run(processor, buffer)

        assertArrayEquals(expected, out)
    }

    @Test
    fun `at the full cut a 50 Hz tone comes out about 23 dB down and a 1 kHz tone unchanged`() {
        val low = processor().apply { setGainDb(-24f) }
        low.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertEquals(-23.2, peakDb16(run(low, shorts(tone16(50.0, 44_100)))), 0.6)

        val high = processor().apply { setGainDb(-24f) }
        high.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertEquals(0.0, peakDb16(run(high, shorts(tone16(1_000.0, 44_100)))), 0.2)
    }

    @Test
    fun `a gain set before the first configure is kept`() {
        val processor = ShelfAudioProcessor(slice3)
        processor.setGainDb(-24f)

        processor.configure(AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        assertEquals(-23.2, peakDb16(run(processor, shorts(tone16(50.0, 44_100)))), 0.6)
    }

    @Test
    fun `a flush snaps to the target so a preloaded player starts cut`() {
        val processor = processor().apply { setGainDb(-24f) }

        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val first = run(processor, shorts(tone16(50.0, 2_000)))

        // With the ramp from flat the first 20 ms would still be loud; after a snap the whole buffer is cut.
        val buffer = ByteBuffer.wrap(first).order(ByteOrder.nativeOrder()).asShortBuffer()
        assertTrue((0 until buffer.limit()).all { abs(buffer.get(it).toInt()) < 6_000 })
    }

    @Test
    fun `bypass ramps out and then the samples pass untouched again`() {
        val processor = processor().apply { setGainDb(-24f) }
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        run(processor, shorts(tone16(50.0, 4_410)))

        processor.bypass()
        run(processor, shorts(tone16(50.0, 4_410)))
        val input = tone16(50.0, 4_410)
        val out = run(processor, shorts(input))

        val expected = ByteArray(input.size * 2).also { shorts(input).get(it) }
        assertArrayEquals(expected, out)
    }

    @Test
    fun `an unsupported format makes the processor inactive instead of failing`() {
        val processor = ShelfAudioProcessor(slice3)

        val format = processor.configure(AudioFormat(rate, 2, C.ENCODING_PCM_8BIT))

        assertEquals(AudioFormat.NOT_SET, format)
        assertFalse(processor.isActive)
    }

    @Test
    fun `the output is as long as the input`() {
        val processor = processor().apply { setGainDb(-24f) }

        assertEquals(8_820 * 2, run(processor, shorts(tone16(60.0, 4_410))).size)
    }
}
