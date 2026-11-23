package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The five normalised coefficients of a biquad (`a0` is 1): `y = b0 x + z1`, with the state of [Biquad]. The low
 * shelf is the RBJ cookbook one with slope 1, which puts the corner half way down (12 dB for a 24 dB cut).
 */
data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    /** The gain in dB at [freqHz] for audio at [sampleRate]: the filter's response, evaluated on the unit circle. */
    fun magnitudeDb(
        freqHz: Double,
        sampleRate: Int,
    ): Double {
        val w = 2 * PI * freqHz / sampleRate
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2 * w)
        val s2 = sin(2 * w)
        val numRe = b0 + b1 * c1 + b2 * c2
        val numIm = -(b1 * s1 + b2 * s2)
        val denRe = 1 + a1 * c1 + a2 * c2
        val denIm = -(a1 * s1 + a2 * s2)
        return DB_PER_DECADE * log10(hypot(numRe, numIm) / hypot(denRe, denIm))
    }

    companion object {
        private const val DB_PER_DECADE = 20.0

        /** Identity: `y = x`, bit for bit on a filter with no state. */
        val PASS_THROUGH = BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)

        /** A low shelf at [freqHz] with [gainDb] below the corner and 0 dB above it; 0 dB gives [PASS_THROUGH]. */
        fun lowShelf(
            sampleRate: Int,
            freqHz: Double,
            gainDb: Double,
        ): BiquadCoefficients {
            if (gainDb == 0.0) return PASS_THROUGH
            val a = 10.0.pow(gainDb / DB_TO_AMPLITUDE_SQRT_DIVISOR)
            val w = 2 * PI * freqHz / sampleRate
            val cosW = cos(w)
            val alpha = sin(w) / 2 * sqrt(2.0)
            val beta = 2 * sqrt(a) * alpha
            val a0 = (a + 1) + (a - 1) * cosW + beta
            return BiquadCoefficients(
                b0 = a * ((a + 1) - (a - 1) * cosW + beta) / a0,
                b1 = 2 * a * ((a - 1) - (a + 1) * cosW) / a0,
                b2 = a * ((a + 1) - (a - 1) * cosW - beta) / a0,
                a1 = -2 * ((a - 1) + (a + 1) * cosW) / a0,
                a2 = ((a + 1) + (a - 1) * cosW - beta) / a0,
            )
        }

        private const val DB_TO_AMPLITUDE_SQRT_DIVISOR = 40.0
    }
}

/**
 * One channel of a biquad in transposed direct form II: the state is two numbers, so replacing [coefficients] while
 * audio runs does not make the output jump. Pure arithmetic, no allocation per sample.
 */
class Biquad(
    var coefficients: BiquadCoefficients,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Float): Float {
        val input = x.toDouble()
        val c = coefficients
        val y = c.b0 * input + z1
        z1 = c.b1 * input - c.a1 * y + z2
        z2 = c.b2 * input - c.a2 * y
        return y.toFloat()
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }
}
