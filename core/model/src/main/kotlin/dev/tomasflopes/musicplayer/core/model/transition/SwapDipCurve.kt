package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.pow

/**
 * A volume move on the bass swap (design phase 7b, ADR 0110): the outgoing song also loses [dipDb] of level, linear in
 * dB, over the same stretch in which its bass is cut, and keeps it until the [base] curve takes it to 0. The incoming
 * song is the [base] curve untouched (it cannot go above 1). A level that falls with the bass reads as a hand-over
 * even where the phone speaker plays little bass. The curve starts at 1 and ends at 0, stays in 0..1 and never rises,
 * as [DjVolumeCurve] does, so the engine's contract holds; with a [dipDb] of 0 or more it is [base].
 */
class SwapDipCurve(
    private val base: GainCurve,
    private val bass: BassCurve,
    dipDb: Float,
) : GainCurve {
    private val dipDb = dipDb.coerceAtMost(0f)

    override fun outgoing(progress: Float): Float = base.outgoing(progress) * dipGain(progress)

    override fun incoming(progress: Float): Float = base.incoming(progress)

    private fun dipGain(progress: Float): Float =
        if (dipDb == 0f) 1f else DB_BASE.pow(dipDb * bass.outgoingFraction(progress) / DB_PER_DECADE)

    private companion object {
        const val DB_BASE = 10f
        const val DB_PER_DECADE = 20f
    }
}
