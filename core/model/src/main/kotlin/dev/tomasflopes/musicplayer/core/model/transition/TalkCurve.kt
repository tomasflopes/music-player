package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The volumes of a talking-outro overlap (design phase 7, slice 4, ADR 0104). The incoming song rises from 0 to 1
 * over [entryMs] and stays there. The outgoing song ducks from 1 to [duckGain] over [duckMs] (linear in dB), holds,
 * and falls from [duckGain] to 0 on a quarter cosine over the last [fadeMs]. Times are real time from the overlap
 * start; [overlapMs] turns progress into time. The two parts of the outgoing song never overlap in time when the
 * constructor's checks hold, which [TalkCurve.of] makes sure of.
 */
class TalkCurve(
    private val overlapMs: Double,
    private val entryMs: Double,
    private val duckMs: Double,
    private val fadeMs: Double,
    private val duckDb: Float,
) : GainCurve {
    private val duckGain = dbToGain(duckDb)

    override fun outgoing(progress: Float): Float =
        when {
            progress.isNaN() || progress <= 0f -> 1f
            progress >= 1f -> 0f
            else -> outgoingAt(progress * overlapMs)
        }

    override fun incoming(progress: Float): Float =
        when {
            progress.isNaN() || progress <= 0f -> 0f
            else -> {
                val t = progress * overlapMs
                if (t >= entryMs) 1f else sin(t / entryMs * HALF_PI).toFloat()
            }
        }

    private fun outgoingAt(t: Double): Float {
        val fadeStart = overlapMs - fadeMs
        return when {
            t < duckMs -> dbToGain(duckDb * (t / duckMs).toFloat())
            t < fadeStart -> duckGain
            else -> (duckGain * cos((t - fadeStart) / fadeMs * HALF_PI)).toFloat()
        }
    }

    companion object {
        private const val HALF_PI = PI / 2
        private const val DB_PER_DECADE = 20.0

        private fun dbToGain(db: Float): Float = 10.0.pow(db / DB_PER_DECADE).toFloat()

        /**
         * The curve of an overlap of [overlapMs]: a [beatMs] entry, a [barMs] duck by [duckDb] and a fade over
         * [fadeBars] bars, each shortened so that entry, duck and fade fit. Null when no fade of at least
         * [MIN_FADE_MS] fits after the duck.
         */
        fun of(
            overlapMs: Long,
            beatMs: Double,
            barMs: Double,
            fadeBars: Int,
            duckDb: Float,
        ): TalkCurve? {
            val duck = minOf(barMs, overlapMs * MAX_DUCK_SHARE)
            val fade = minOf(fadeBars * barMs, overlapMs - duck)
            val entry = minOf(beatMs, overlapMs.toDouble())
            val valid = overlapMs > 0 && beatMs > 0.0 && barMs > 0.0
            return if (!valid || fade < MIN_FADE_MS) {
                null
            } else {
                TalkCurve(overlapMs.toDouble(), entry, duck, fade, duckDb)
            }
        }

        const val MIN_FADE_MS = 1_000.0
        private const val MAX_DUCK_SHARE = 0.25
    }
}
