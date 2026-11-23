package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.roundToLong

/**
 * The bass schedule of a beat-match overlap (design phase 7, slice 3): the gain in dB of the bass shelf of each
 * player. The incoming song starts at [cutDb] (full cut); from [swapStartMs] of the overlap, over [swapMs], the
 * incoming goes to 0 dB and the outgoing goes to [cutDb], and the outgoing keeps the cut until it ends. Linear in dB,
 * so there is no step. The functions read the overlap [progress] (0..1), like [GainCurve], so a pause or a seek of the
 * incoming position cannot desync them.
 */
class BassCurve(
    val overlapMs: Long,
    val swapStartMs: Long,
    val swapMs: Long,
    val cutDb: Float,
) {
    /** The incoming player's shelf gain: [cutDb] before the swap, 0 after it. */
    fun incomingDb(progress: Float): Float = cutDb * (1f - swapFraction(progress))

    /** The outgoing player's shelf gain: 0 before the swap, [cutDb] after it. */
    fun outgoingDb(progress: Float): Float = cutDb * swapFraction(progress)

    private fun swapFraction(progress: Float): Float {
        val elapsed = (if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)).toDouble() * overlapMs
        return if (swapMs <= 0) {
            if (elapsed >= swapStartMs) 1f else 0f
        } else {
            ((elapsed - swapStartMs) / swapMs).coerceIn(0.0, 1.0).toFloat()
        }
    }

    companion object {
        /**
         * The curve for an overlap of [overlapMs] real milliseconds: the swap starts on the middle bar of the
         * overlap on the [outgoingGrid] (the bar at index `bars / 2`, counting from the overlap start, which is on a
         * bar start of that grid) and takes [TransitionConfig.bassSwapBeats] beats of it.
         */
        fun forOverlap(
            overlapMs: Long,
            outgoingGrid: BeatGrid,
            config: TransitionConfig,
        ): BassCurve {
            val bars = (overlapMs / outgoingGrid.barMs).roundToLong().toInt()
            return BassCurve(
                overlapMs = overlapMs,
                swapStartMs = (bars / 2 * outgoingGrid.barMs).roundToLong(),
                swapMs = (config.bassSwapBeats * outgoingGrid.beatMs).roundToLong(),
                cutDb = config.bassCutDb,
            )
        }
    }
}
