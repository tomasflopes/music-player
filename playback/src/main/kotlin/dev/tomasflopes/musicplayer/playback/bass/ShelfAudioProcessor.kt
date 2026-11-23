package dev.tomasflopes.musicplayer.playback.bass

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import dev.tomasflopes.musicplayer.core.model.transition.BassShelf
import dev.tomasflopes.musicplayer.core.model.transition.TransitionConfig
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Implementation 1 of [BassControl] (ADR 0101, 0102): the pure-Kotlin [BassShelf] as a Media3 audio processor, in the
 * sink chain of one player. It takes 16-bit and float PCM; any other format makes it inactive (the chain skips it).
 * While the shelf is flat the input is copied through untouched, byte for byte. It is always active in the chain,
 * because Media3 reads a processor's active state only at a flush. The target is kept here, so a call before the
 * first configure is not lost, and a flush snaps the shelf to it.
 */
@UnstableApi
class ShelfAudioProcessor(
    private val config: TransitionConfig = TransitionConfig(),
) : BaseAudioProcessor(),
    BassControl {
    @Volatile private var shelf: BassShelf? = null

    @Volatile private var requestedDb = 0f
    private var scratch = FloatArray(0)

    override val targetDb: Float get() = requestedDb

    override fun setGainDb(db: Float) {
        if (db.isNaN()) return
        requestedDb = db.coerceIn(config.bassCutDb, 0f)
        shelf?.setTargetDb(requestedDb)
    }

    override fun bypass() = setGainDb(0f)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val encoding = inputAudioFormat.encoding
        val supported =
            (encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT) &&
                inputAudioFormat.channelCount in 1..MAX_CHANNELS
        if (!supported) {
            shelf = null
            return AudioFormat.NOT_SET
        }
        shelf =
            BassShelf(
                sampleRate = inputAudioFormat.sampleRate,
                channels = inputAudioFormat.channelCount,
                cornerHz = config.bassCutHz.toDouble(),
                depthDb = config.bassCutDb,
            ).also { it.snapToDb(requestedDb) }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val active = shelf?.takeUnless { it.isPassThrough }
        if (active == null) {
            output.put(inputBuffer)
        } else {
            filter(active, inputBuffer, output)
        }
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        shelf?.snapToDb(requestedDb)
    }

    override fun onReset() {
        shelf = null
        scratch = FloatArray(0)
    }

    private fun filter(
        active: BassShelf,
        input: ByteBuffer,
        output: ByteBuffer,
    ) {
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val channels = inputAudioFormat.channelCount
        val frames = input.remaining() / ((if (float) Float.SIZE_BYTES else Short.SIZE_BYTES) * channels)
        val samples = frames * channels
        if (scratch.size < samples) scratch = FloatArray(samples)
        for (i in 0 until samples) {
            scratch[i] = if (float) input.getFloat() else input.getShort() / PCM16_SCALE
        }
        active.process(scratch, frames)
        for (i in 0 until samples) {
            if (float) {
                output.putFloat(scratch[i])
            } else {
                val scaled = (scratch[i] * PCM16_SCALE).roundToInt()
                output.putShort(scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
        // A buffer that does not end on a frame: the rest goes through as it is.
        output.put(input)
    }

    private companion object {
        const val PCM16_SCALE = 32768f
        const val MAX_CHANNELS = 8
    }
}
