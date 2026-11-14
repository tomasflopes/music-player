package dev.tomasflopes.musicplayer.core.taste.config

/**
 * The numbers behind the skip-aware Recently added shuffle (design §7.7, ADR 0050). Kept beside
 * [SmartShuffleConfig]; defaults are the owner's starting values.
 */
data class SkipAwareShuffleConfig(
    /** A song scoring -s gets the weight 1 / (1 + [penaltyPerPoint] * s): at -2 it is half as likely to come early. */
    val penaltyPerPoint: Double = 0.5,
    /** The lowest weight, so even a heavily skipped song still plays, late. */
    val minWeight: Double = 0.05,
) {
    init {
        require(penaltyPerPoint.isFinite() && penaltyPerPoint >= 0) { "penaltyPerPoint must not be negative" }
        require(minWeight > 0.0 && minWeight <= 1.0) { "minWeight must be in (0, 1]: $minWeight" }
    }

    /** 1 for a score of 0 or more; shrinking towards [minWeight] below that. */
    fun weightOf(score: Double): Double =
        if (score >= 0.0) 1.0 else maxOf(minWeight, 1.0 / (1.0 + penaltyPerPoint * -score))
}
