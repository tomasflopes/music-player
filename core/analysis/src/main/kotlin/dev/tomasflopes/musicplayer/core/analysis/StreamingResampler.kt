package dev.tomasflopes.musicplayer.core.analysis

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

private const val ZERO_CROSSINGS = 8.0
private const val CUTOFF_SHARE = 0.45
private const val HALF = 0.5
private const val TRIM_AFTER = 4_096

/**
 * Windowed-sinc resampler that takes input in chunks of any size (ADR 0065). Every output sample is computed from
 * absolute input positions with exact integer phase arithmetic, so the result does not depend on how the input was
 * chunked. Equal rates pass the samples through untouched.
 */
class StreamingResampler(
    inRate: Int,
    outRate: Int,
) {
    private val passthrough = inRate == outRate
    private val divisor = gcd(inRate, outRate)
    private val step = (inRate / divisor).toLong()
    private val phases = (outRate / divisor).toLong().toInt()
    private val firstTap = IntArray(phases)
    private val weights = Array(phases) { DoubleArray(0) }

    private var buffer = FloatArray(0)
    private var bufferStart = 0L
    private var bufferLength = 0
    private var totalIn = 0L
    private var nextOut = 0L

    init {
        require(inRate > 0 && outRate > 0) { "Sample rates must be positive" }
        if (!passthrough) buildKernels(inRate, outRate)
    }

    /** Feeds the first [count] samples of [input]; returns the output samples that are now complete. */
    fun push(
        input: FloatArray,
        count: Int = input.size,
    ): FloatArray {
        if (passthrough) return input.copyOf(count)
        append(input, count)
        val out = produce(final = false)
        trim()
        return out
    }

    /** Ends the stream, treating what follows the last sample as silence, and returns the remaining output. */
    fun finish(): FloatArray = if (passthrough) FloatArray(0) else produce(final = true)

    private fun buildKernels(
        inRate: Int,
        outRate: Int,
    ) {
        val cutoff = CUTOFF_SHARE * min(1.0, outRate.toDouble() / inRate)
        val radius = ZERO_CROSSINGS / (2.0 * cutoff)
        for (phase in 0 until phases) {
            val offset = phase.toDouble() / phases
            val low = floor(offset - radius).toInt() + 1
            val high = ceil(offset + radius).toInt() - 1
            val taps = DoubleArray(high - low + 1) { kernel(low + it - offset, cutoff, radius) }
            val sum = taps.sum()
            for (i in taps.indices) taps[i] /= sum
            firstTap[phase] = low
            weights[phase] = taps
        }
    }

    private fun kernel(
        distance: Double,
        cutoff: Double,
        radius: Double,
    ): Double {
        if (kotlin.math.abs(distance) >= radius) return 0.0
        val x = 2.0 * cutoff * distance
        val sinc = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)
        val hann = HALF * (1.0 + cos(PI * distance / radius))
        return 2.0 * cutoff * sinc * hann
    }

    private fun append(
        input: FloatArray,
        count: Int,
    ) {
        if (bufferLength + count > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, bufferLength + count))
        System.arraycopy(input, 0, buffer, bufferLength, count)
        bufferLength += count
        totalIn += count
    }

    private fun produce(final: Boolean): FloatArray {
        val out = FloatSink()
        while (nextOutputIsReady(final)) {
            val numerator = nextOut * step
            val position = numerator / phases
            val phase = (numerator % phases).toInt()
            val taps = weights[phase]
            var sum = 0.0
            for (t in taps.indices) sum += taps[t] * sampleAt(position + firstTap[phase] + t)
            out.add(sum.toFloat())
            nextOut++
        }
        return out.toArray()
    }

    /** True when every input sample the next output needs is here (or, at the end, when it lies inside the song). */
    private fun nextOutputIsReady(final: Boolean): Boolean {
        val numerator = nextOut * step
        val position = numerator / phases
        val phase = (numerator % phases).toInt()
        return if (final) position < totalIn else position + firstTap[phase] + weights[phase].size <= totalIn
    }

    private fun sampleAt(index: Long): Double =
        if (index < 0L || index >= totalIn) 0.0 else buffer[(index - bufferStart).toInt()].toDouble()

    private fun trim() {
        val numerator = nextOut * step
        val earliest = numerator / phases + (firstTap.minOrNull() ?: 0)
        val drop = (earliest - bufferStart).coerceAtMost(bufferLength.toLong()).toInt()
        if (drop < TRIM_AFTER) return
        System.arraycopy(buffer, drop, buffer, 0, bufferLength - drop)
        bufferLength -= drop
        bufferStart += drop
    }

    private fun gcd(
        a: Int,
        b: Int,
    ): Int = if (b == 0) a else gcd(b, a % b)
}
