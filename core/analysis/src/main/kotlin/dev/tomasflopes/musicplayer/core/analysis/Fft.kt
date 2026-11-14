package dev.tomasflopes.musicplayer.core.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place radix-2 FFT of a fixed power-of-two [size]. Tables are built once, so one instance serves every frame. */
internal class Fft(
    val size: Int,
) {
    private val cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2.0 * PI * it / size) }
    private val reversed = IntArray(size)

    init {
        require(size > 1 && size and (size - 1) == 0) { "FFT size must be a power of two, was $size" }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) reversed[i] = Integer.reverse(i) ushr (Int.SIZE_BITS - bits)
    }

    fun transform(
        re: DoubleArray,
        im: DoubleArray,
    ) {
        permute(re, im)
        var half = 1
        while (half < size) {
            butterflies(re, im, half)
            half *= 2
        }
    }

    private fun permute(
        re: DoubleArray,
        im: DoubleArray,
    ) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
    }

    private fun butterflies(
        re: DoubleArray,
        im: DoubleArray,
        half: Int,
    ) {
        val step = size / (half * 2)
        var start = 0
        while (start < size) {
            for (k in 0 until half) {
                val wr = cosTable[k * step]
                val wi = -sinTable[k * step]
                val i = start + k
                val j = i + half
                val tr = re[j] * wr - im[j] * wi
                val ti = re[j] * wi + im[j] * wr
                re[j] = re[i] - tr
                im[j] = im[i] - ti
                re[i] += tr
                im[i] += ti
            }
            start += half * 2
        }
    }
}
