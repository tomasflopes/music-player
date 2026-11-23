package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The volumes of a talking-outro overlap (design phase 7, slices 4 and 4b, ADR 0104, 0107). The incoming song rises
 * from 0 to [heldGain] over [entryMs], holds that flat level for the whole talk, and only over the last [fadeMs] rises
 * from [heldGain] to 1 on a quarter sine. The outgoing song ducks from 1 to [duckGain] over [duckMs] (linear in dB),
 * holds, and falls from [duckGain] to 0 on a quarter cosine over the same last [fadeMs]. Times are real time from the
 * overlap start; [overlapMs] turns progress into time. The parts never overlap in time when [TalkCurve.of] built the
 * curve.
 */
class TalkCurve(
    private val overlapMs: Double,
    private val entryMs: Double,
    private val duckMs: Double,
    private val fadeMs: Double,
    private val duckDb: Float,
    private val heldDb: Float,
) : GainCurve {
    private val duckGain = dbToGain(duckDb)
    private val heldGain = dbToGain(heldDb)

    override fun outgoing(progress: Float): Float =
        when {
            progress.isNaN() || progress <= 0f -> 1f
            progress >= 1f -> 0f
            else -> outgoingAt(progress * overlapMs)
        }

    override fun incoming(progress: Float): Float =
        when {
            progress.isNaN() || progress <= 0f -> 0f
            progress >= 1f -> 1f
            else -> incomingAt(progress * overlapMs)
        }

    private fun incomingAt(t: Double): Float {
        val fadeStart = overlapMs - fadeMs
        return when {
            t < entryMs -> (heldGain * sin(t / entryMs * HALF_PI)).toFloat()
            t < fadeStart -> heldGain
            else -> (heldGain + (1f - heldGain) * sin((t - fadeStart) / fadeMs * HALF_PI)).toFloat()
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
         * The curve of an overlap of [overlapMs]: a [beatMs] entry up to [heldDb], a [barMs] duck of the outgoing song
         * by [duckDb] and a final fade over [fadeBars] bars, each shortened so that entry, duck and fade fit (the entry
         * ends no later than the fade starts). [heldDb] is clamped to at most 0 dB. Null when no fade of at least
         * [MIN_FADE_MS] fits after the duck.
         */
        fun of(
            overlapMs: Long,
            beatMs: Double,
            barMs: Double,
            fadeBars: Int,
            duckDb: Float,
            heldDb: Float,
        ): TalkCurve? {
            val duck = minOf(barMs, overlapMs * MAX_DUCK_SHARE)
            val fade = minOf(fadeBars * barMs, overlapMs - duck)
            val entry = minOf(beatMs, overlapMs - fade)
            val valid = overlapMs > 0 && beatMs > 0.0 && barMs > 0.0
            return if (!valid || fade < MIN_FADE_MS) {
                null
            } else {
                TalkCurve(overlapMs.toDouble(), entry, duck, fade, duckDb, minOf(heldDb, 0f))
            }
        }

        const val MIN_FADE_MS = 1_000.0
        private const val MAX_DUCK_SHARE = 0.25
    }
}
