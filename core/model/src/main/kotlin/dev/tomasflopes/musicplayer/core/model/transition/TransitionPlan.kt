package dev.tomasflopes.musicplayer.core.model.transition

import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.crossfade.FadeCurve
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The volume of the outgoing and the incoming player at a point of an overlap, one function each, no allocation. */
interface GainCurve {
    /** [progress] is the fraction of the overlap that has passed, clamped into 0..1. Result in 0..1. */
    fun outgoing(progress: Float): Float

    fun incoming(progress: Float): Float
}

/** The phase 6 curve (ADR 0062), as a [GainCurve]: [FadeCurve] itself, so the numbers are the same. */
object EqualPowerCurve : GainCurve {
    override fun outgoing(progress: Float) = FadeCurve.gains(progress).outgoing

    override fun incoming(progress: Float) = FadeCurve.gains(progress).incoming
}

/**
 * The DJ volume curve (design phase 7): the incoming song rises from 0 to 1 over the first half of the overlap, the
 * outgoing song holds at 1 until the half and then falls from 1 to 0 over the second half, each segment equal-power.
 */
object DjVolumeCurve : GainCurve {
    private const val HALF = 0.5f

    override fun outgoing(progress: Float): Float =
        when {
            progress.isNaN() || progress <= HALF -> 1f
            progress >= 1f -> 0f
            else -> cos(((progress - HALF) / HALF).toDouble() * PI / 2).toFloat()
        }

    override fun incoming(progress: Float): Float =
        when {
            progress.isNaN() || progress <= 0f -> 0f
            progress >= HALF -> 1f
            else -> sin((progress / HALF).toDouble() * PI / 2).toFloat()
        }
}

/** One step of the speed schedule after an overlap (slice 2): at [atMs] of the incoming position, speed [speed]. */
data class SpeedStep(
    val atMs: Long,
    val speed: Float,
)

/** What to do at one song-to-song transition. Plain data: nothing here knows a player. */
sealed interface TransitionPlan {
    /** The technique that made the plan, for the lab and the log. */
    val technique: String

    /** Join the songs the plain way (phase 6 hard cut). */
    data object HardCut : TransitionPlan {
        override val technique = "hard-cut"
    }

    /** The phase 6 crossfade over [durationMs], from the end of the outgoing song. */
    data class Fade(
        val durationMs: Long,
    ) : TransitionPlan {
        override val technique = "crossfade"
    }

    /**
     * Two songs playing together from [switchPointMs] of the outgoing song, the incoming one starting at
     * [incomingStartMs] of its own file, for [overlapMs]. [incomingSpeed] is the incoming speed during the overlap and
     * [easeBack] how it returns to 1.0 (both 1.0 and empty in slice 1). [ratio] is outgoing tempo over incoming tempo.
     */
    class Overlap(
        override val technique: String,
        val switchPointMs: Long,
        val incomingStartMs: Long,
        val overlapMs: Long,
        val curve: GainCurve,
        val ratio: Double = 1.0,
        val incomingSpeed: Float = 1f,
        val easeBack: List<SpeedStep> = emptyList(),
    ) : TransitionPlan

    companion object {
        /** The plan of a phase 6 decision. */
        fun of(decision: CrossfadeDecision): TransitionPlan =
            when (decision) {
                is CrossfadeDecision.Fade -> Fade(decision.durationMs)
                CrossfadeDecision.HardCut -> HardCut
            }
    }
}
