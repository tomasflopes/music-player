package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.roundToLong

/**
 * The bass schedule of a beat-match overlap (design phase 7, slice 3): the gain in dB of the bass shelf of each
 * player. The incoming song starts at [cutDb] (full cut); from [swapStartMs] of the overlap, over [swapMs], the
 * incoming goes to 0 dB, and the outgoing goes to [cutDb] and keeps the cut until it ends. The outgoing cut may start
 * [outgoingLeadMs] earlier than the incoming opens (phase 7b, ADR 0110) and still ends with it, so the outgoing bass is
 * gone before the incoming one is fully there. Linear in dB, so there is no step. The functions read the overlap
 * [progress] (0..1), like [GainCurve], so a pause or a seek of the incoming position cannot desync them.
 */
class BassCurve(
    val overlapMs: Long,
    val swapStartMs: Long,
    val swapMs: Long,
    val cutDb: Float,
    outgoingLeadMs: Long = 0,
) {
    /** The lead, kept inside the overlap: the outgoing cut cannot start before the overlap does. */
    val outgoingLeadMs: Long = outgoingLeadMs.coerceIn(0, swapStartMs.coerceAtLeast(0))

    /**
     * The incoming player's shelf gain: [cutDb] before the swap, 0 after it. The `+ 0f` turns a negative zero (cut
     * times 0) into 0f, so the flat end compares equal to 0f.
     */
    fun incomingDb(progress: Float): Float = cutDb * (1f - fraction(progress, swapStartMs, swapMs)) + 0f

    /** The outgoing player's shelf gain: 0 before its cut starts, [cutDb] after the swap. */
    fun outgoingDb(progress: Float): Float = cutDb * outgoingFraction(progress) + 0f

    /** How far the outgoing cut has gone, 0..1: the same schedule that moves the outgoing shelf (ADR 0110). */
    fun outgoingFraction(progress: Float): Float =
        fraction(progress, swapStartMs - outgoingLeadMs, swapMs + outgoingLeadMs)

    private fun fraction(
        progress: Float,
        startMs: Long,
        lengthMs: Long,
    ): Float {
        val elapsed = (if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)).toDouble() * overlapMs
        return if (lengthMs <= 0) {
            if (elapsed >= startMs) 1f else 0f
        } else {
            ((elapsed - startMs) / lengthMs).coerceIn(0.0, 1.0).toFloat()
        }
    }

    companion object {
        /**
         * The curve for an overlap of [overlapMs] real milliseconds: the swap starts on the middle bar of the
         * overlap on the [outgoingGrid] (the bar at index `bars / 2`, counting from the overlap start, which is on a
         * bar start of that grid) and takes [TransitionConfig.bassSwapBeats] beats of it; the outgoing cut starts
         * [TransitionConfig.bassOutgoingLeadBeats] beats before it.
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
                outgoingLeadMs = (config.bassOutgoingLeadBeats * outgoingGrid.beatMs).roundToLong(),
            )
        }
    }
}
