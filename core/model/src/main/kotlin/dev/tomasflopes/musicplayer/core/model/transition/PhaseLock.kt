package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Phase lock for one overlap (design phase 7, slice 2, ADR 0096): on each tick it reads the beat-phase error and says
 * what the incoming speed should be, or that the beat match is lost. The speed stays within [TransitionConfig.maxNudge]
 * of the planned speed, proportional to the error and full at [FULL_NUDGE_MS]. A positive error means the incoming
 * beat came before the outgoing one, so the incoming song slows down. If the error stays above
 * [TransitionConfig.maxPhaseErrorMs] for [TransitionConfig.phaseLockAbandonBars] bars of ticks, the answer is
 * [Action.Abandon]. Pure and allocation-light: one instance per overlap, no clock, the caller sets the tick.
 */
class PhaseLock(
    private val config: TransitionConfig,
    private val outgoingGrid: BeatGrid,
    private val incomingGrid: BeatGrid,
    private val plannedSpeed: Double,
) {
    sealed interface Action {
        /** Play the incoming song at [value] from now on. */
        data class Speed(
            val value: Float,
        ) : Action

        /** Stop matching beats: the rest of the overlap is an equal-power fade. */
        data object Abandon : Action
    }

    private val abandonTicks: Int =
        ceil(config.phaseLockAbandonBars * outgoingGrid.barMs / config.phaseLockTickMs).toInt().coerceAtLeast(1)
    private var overTicks = 0

    /** Positions are file positions; [currentSpeed] is what the incoming player runs at now. */
    fun update(
        outgoingMs: Double,
        incomingMs: Double,
        currentSpeed: Double,
    ): Action {
        val error = BeatPhase.errorMs(outgoingGrid, outgoingMs, incomingGrid, incomingMs, currentSpeed)
        overTicks = if (abs(error) > config.maxPhaseErrorMs) overTicks + 1 else 0
        if (overTicks >= abandonTicks) return Action.Abandon
        val nudge = (-error * config.maxNudge / FULL_NUDGE_MS).coerceIn(-config.maxNudge, config.maxNudge)
        return Action.Speed((plannedSpeed + nudge).toFloat())
    }

    companion object {
        /** The error at which the nudge reaches [TransitionConfig.maxNudge]. */
        const val FULL_NUDGE_MS = 20.0
    }
}
