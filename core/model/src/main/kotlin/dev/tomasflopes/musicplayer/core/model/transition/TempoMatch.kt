package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.abs

/**
 * How the incoming tempo is read against the outgoing one (design phase 7, slice 2, ADR 0094). [multiplier] is 0.5, 1
 * or 2 (half, same or double tempo of the incoming song); [ratio] is the outgoing tempo over the incoming tempo as
 * read, which is also the speed the incoming song plays at so that its beats fall on the outgoing beats.
 */
data class TempoMatch(
    val multiplier: Double,
    val ratio: Double,
) {
    /** How far the incoming song is stretched, as |r - 1|. */
    val stretch: Double get() = abs(ratio - 1.0)
}

/** Picks the reading of the incoming tempo that needs the least stretch. Pure arithmetic. */
object TempoMatcher {
    /** Tried in this order, so on a tie the plain reading wins over half and double. */
    private val MULTIPLIERS = doubleArrayOf(1.0, 0.5, 2.0)

    /** Absorbs the rounding of a ratio that sits exactly on the limit, like 108 over 100 for 8 %. */
    private const val LIMIT_EPSILON = 1e-9

    /**
     * The smallest |r - 1| over the incoming tempo at x0.5, x1 and x2 against [outgoingBpm], or null when even the
     * best one is above [maxStretch]. Only the stored beat tempo is read: the first beat of ADR 0065 is the phase of
     * that tempo, so the other tempo candidates have no grid to line up with (ADR 0094).
     */
    fun best(
        outgoingBpm: Double,
        incomingBpm: Double,
        maxStretch: Double,
    ): TempoMatch? {
        if (!(outgoingBpm > 0.0) || !(incomingBpm > 0.0)) return null
        var best: TempoMatch? = null
        for (multiplier in MULTIPLIERS) {
            val candidate = TempoMatch(multiplier, outgoingBpm / (incomingBpm * multiplier))
            if (best == null || candidate.stretch < best.stretch) best = candidate
        }
        return best?.takeIf { it.stretch <= maxStretch + LIMIT_EPSILON }
    }
}
