package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.core.analysis.config.SpectrumConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

private const val HALF = 0.5

/**
 * The cost of the spectrum framing and nothing else: Hann window, FFT and a sum of magnitudes per frame. It stands in
 * for the real features of slice 2 so the timing spike can say how much of the budget the spectrum takes (ADR 0054).
 */
class SpectrumCostStub(
    private val config: SpectrumConfig,
) {
    private val fft = Fft(config.fftSize)
    private val window = DoubleArray(config.fftSize) { HALF - HALF * cos(2.0 * PI * it / config.fftSize) }

    /** Number of whole frames in [sampleCount] samples. */
    fun frameCount(sampleCount: Int): Int =
        if (sampleCount < config.fftSize) 0 else (sampleCount - config.fftSize) / config.hop + 1

    /** Runs every frame of [mono]; the returned sum only exists so the work cannot be optimised away. */
    fun run(mono: FloatArray): Double {
        val re = DoubleArray(config.fftSize)
        val im = DoubleArray(config.fftSize)
        var checksum = 0.0
        for (frame in 0 until frameCount(mono.size)) {
            val start = frame * config.hop
            for (i in 0 until config.fftSize) {
                re[i] = mono[start + i] * window[i]
                im[i] = 0.0
            }
            fft.transform(re, im)
            checksum += magnitudeSum(re, im)
        }
        return checksum
    }

    private fun magnitudeSum(
        re: DoubleArray,
        im: DoubleArray,
    ): Double {
        var sum = 0.0
        for (bin in 0..config.fftSize / 2) sum += sqrt(re[bin] * re[bin] + im[bin] * im[bin])
        return sum
    }
}
