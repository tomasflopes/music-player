package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The schedule that brings the incoming speed back to 1.0 after an overlap (design phase 7, slice 2, ADR 0095).
 * Positions are file positions of the incoming song, so the schedule does not depend on a clock.
 */
object EaseBack {
    private const val NO_STRETCH = 1e-6
    private const val STEP_EPSILON = 1e-9

    /**
     * From [speed] to 1.0, starting at [startMs] (the incoming position where the overlap ends, a bar start of
     * [incomingGrid]). Stepped: one step on each bar start, each at most [TransitionConfig.maxSpeedStep], over
     * [TransitionConfig.easeBars] bars, or over as many bars as the steps need when the stretch is larger than
     * easeBars steps can cover. Single step ([TransitionConfig.singleStepEaseBack]): 1.0 at the first phrase start
     * at or after [startMs]. Empty when the speed is already 1.0. The last step is exactly 1.0.
     */
    fun steps(
        config: TransitionConfig,
        speed: Double,
        incomingGrid: BeatGrid,
        startMs: Double,
    ): List<SpeedStep> {
        val delta = 1.0 - speed
        return when {
            abs(delta) < NO_STRETCH -> emptyList()
            config.singleStepEaseBack ->
                listOf(SpeedStep(incomingGrid.phraseStartAtOrAfter(startMs).roundToLong(), 1f))
            else -> stepped(config, speed, delta, incomingGrid, startMs)
        }
    }

    private fun stepped(
        config: TransitionConfig,
        speed: Double,
        delta: Double,
        grid: BeatGrid,
        startMs: Double,
    ): List<SpeedStep> {
        val needed = ceil(abs(delta) / config.maxSpeedStep - STEP_EPSILON).toInt()
        val count = max(config.easeBars, needed)
        return (1..count).map { k ->
            val value = if (k == count) 1f else (speed + delta * k / count).toFloat()
            SpeedStep((startMs + (k - 1) * grid.barMs).roundToLong(), value)
        }
    }
}
