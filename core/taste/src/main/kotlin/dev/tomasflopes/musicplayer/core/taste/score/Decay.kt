package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.taste.config.DecayRule
import kotlin.math.pow

/** Exponential decay: a signal loses half its weight every half-life. */
class Decay(
    private val rule: DecayRule = DecayRule(),
) {
    /** Between 0 and 1. A row from the future (the clock moved back) counts as age zero. */
    fun factor(ageMs: Long): Double = HALF.pow(ageMs.coerceAtLeast(0) / rule.halfLifeMs)

    private companion object {
        const val HALF = 0.5
    }
}
