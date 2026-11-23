package dev.tomasflopes.musicplayer.core.model.transition

import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.crossfade.FadeCurve
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
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

/**
 * What is left of an overlap whose beat match was abandoned (ADR 0096): from [fromProgress], where the gains were
 * [incomingAtStart] and [outgoingAtStart], the outgoing song falls to 0 on a quarter cosine and the incoming one rises
 * to 1 on a quarter sine, so the end is the same as the end of an equal-power fade and nothing jumps at the switch.
 */
class EqualPowerTail(
    private val fromProgress: Float,
    private val incomingAtStart: Float,
    private val outgoingAtStart: Float,
) : GainCurve {
    override fun outgoing(progress: Float): Float = outgoingAtStart * cos(local(progress) * HALF_PI).toFloat()

    override fun incoming(progress: Float): Float =
        incomingAtStart + (1f - incomingAtStart) * sin(local(progress) * HALF_PI).toFloat()

    private fun local(progress: Float): Double {
        val span = 1f - fromProgress
        return if (progress.isNaN() ||
            span <= 0f
        ) {
            1.0
        } else {
            ((progress - fromProgress) / span).coerceIn(0f, 1f).toDouble()
        }
    }

    private companion object {
        const val HALF_PI = PI / 2
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
     * [incomingStartMs] of its own file, for [overlapMs] of real time (the outgoing song plays at speed 1.0). The
     * incoming song plays at [incomingSpeed], so it covers [incomingSpeed] times [overlapMs] of its file, and [easeBack]
     * brings the speed back to 1.0 afterwards (positions in its file). [ratio] is outgoing tempo over the incoming
     * tempo as read (equal to [incomingSpeed]). The grids are the two songs' beat grids as matched (the incoming one at
     * the half or double tempo when that was the match); the engine reads them for phase lock. [bass] is the bass swap schedule of the beat
     * match (slice 3); it is null for any other overlap, so the filters stay bypassed.
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
        val outgoingGrid: BeatGrid? = null,
        val incomingGrid: BeatGrid? = null,
        val bass: BassCurve? = null,
    ) : TransitionPlan {
        /** How much of the incoming file the overlap covers, in ms. */
        val incomingSpanMs: Long get() = (overlapMs * incomingSpeed).roundToLong()
    }

    companion object {
        /** The plan of a phase 6 decision. */
        fun of(decision: CrossfadeDecision): TransitionPlan =
            when (decision) {
                is CrossfadeDecision.Fade -> Fade(decision.durationMs)
                CrossfadeDecision.HardCut -> HardCut
            }
    }
}
