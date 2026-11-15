package dev.tomasflopes.musicplayer.core.analysis

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val SILENCE_FLOOR = 1e-3
private const val SILENCE_SHARE = 0.01
private const val STEADY_SHARE = 0.7

/** Where the music really starts and stops, in frames (ADR 0065). All zero for a silent song. */
internal class IntroOutro(
    val musicStart: Int,
    val introEnd: Int,
    val outroStart: Int,
    val musicEnd: Int,
) {
    companion object {
        val NONE = IntroOutro(0, 0, 0, 0)

        /**
         * Silence is a 1 s moving mean of the frame RMS below max(0.001, 1 % of its median). The music starts at the
         * first loud frame inside a loud stretch and ends at the last one; the intro ends when the moving mean first
         * reaches 70 % of the median over the music, and the outro starts when it last does.
         */
        fun find(
            frameRms: FloatArray,
            framesPerSecond: Double,
        ): IntroOutro {
            val smooth = movingMean(frameRms, framesPerSecond.roundToInt())
            val threshold = max(SILENCE_FLOOR, SILENCE_SHARE * median(smooth.filter { it > SILENCE_FLOOR }))
            val loud = frameRms.indices.filter { frameRms[it] >= threshold && smooth[it] >= threshold }
            return if (loud.isEmpty()) NONE else bounds(smooth, loud.first(), loud.last())
        }

        private fun bounds(
            smooth: DoubleArray,
            start: Int,
            end: Int,
        ): IntroOutro {
            val steady = STEADY_SHARE * median(smooth.slice(start..end))
            val introEnd = (start..end).firstOrNull { smooth[it] >= steady } ?: start
            val outroStart = max((start..end).lastOrNull { smooth[it] >= steady } ?: end, introEnd)
            return IntroOutro(start, min(introEnd, outroStart), outroStart, max(end, outroStart))
        }

        private fun movingMean(
            values: FloatArray,
            window: Int,
        ): DoubleArray {
            val prefix = DoubleArray(values.size + 1)
            for (i in values.indices) prefix[i + 1] = prefix[i] + values[i]
            val half = window / 2
            return DoubleArray(values.size) {
                val from = max(0, it - half)
                val to = min(values.size, it + half + 1)
                (prefix[to] - prefix[from]) / (to - from)
            }
        }

        private fun median(values: List<Double>): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sorted()
            return sorted[sorted.size / 2]
        }
    }
}
