package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.ceil
import kotlin.math.floor

/**
 * The beat grid of one song (ADR 0065): beat k falls at `firstBeatMs + k * 60000 / tempoBpm`. Bars count
 * [beatsPerBar] beats from the first beat, phrases [beatsPerPhrase]. Pure arithmetic on milliseconds as doubles.
 */
class BeatGrid(
    val firstBeatMs: Double,
    val tempoBpm: Double,
    val beatsPerBar: Int,
    val beatsPerPhrase: Int,
) {
    val beatMs: Double = MS_PER_MINUTE / tempoBpm
    val barMs: Double = beatMs * beatsPerBar
    val phraseMs: Double = beatMs * beatsPerPhrase

    /** The first bar start at or after [ms] (never before the first beat). */
    fun barStartAtOrAfter(ms: Double): Double {
        val bar = ceil((ms - firstBeatMs) / barMs - EPSILON).coerceAtLeast(0.0)
        return firstBeatMs + bar * barMs
    }

    /** The latest bar start in [low]..[high], or null when none falls there. */
    fun latestBarStart(
        low: Double,
        high: Double,
    ): Double? = latestStart(barMs, low, high)

    /** The latest phrase start in [low]..[high], or null when none falls there. */
    fun latestPhraseStart(
        low: Double,
        high: Double,
    ): Double? = latestStart(phraseMs, low, high)

    /** How far into its beat the song is at [positionMs], in ms, 0 until [beatMs] (before the first beat too). */
    fun beatOffsetMs(positionMs: Double): Double {
        val offset = (positionMs - firstBeatMs) % beatMs
        return if (offset < 0) offset + beatMs else offset
    }

    private fun latestStart(
        unitMs: Double,
        low: Double,
        high: Double,
    ): Double? {
        if (high < low) return null
        val unit = floor((high - firstBeatMs) / unitMs + EPSILON)
        if (unit < 0) return null
        val start = firstBeatMs + unit * unitMs
        return start.takeIf { it >= low - EPSILON_MS }
    }

    companion object {
        private const val MS_PER_MINUTE = 60_000.0
        private const val EPSILON = 1e-9
        private const val EPSILON_MS = 1e-6

        fun of(
            profile: TransitionProfile,
            config: TransitionConfig,
        ) = BeatGrid(profile.firstBeatMs, profile.tempoBpm, config.beatsPerBar, config.beatsPerPhrase)
    }
}

/** The beat-phase error between two playing songs, which the Transition lab shows live (design phase 7). */
object BeatPhase {
    /**
     * How far the incoming beat is behind (positive) or ahead of (negative) the outgoing beat, in ms, wrapped into
     * half a beat either way. Positions are file positions. No allocation, so it can run on every tick.
     */
    fun errorMs(
        outgoing: BeatGrid,
        outgoingPositionMs: Double,
        incoming: BeatGrid,
        incomingPositionMs: Double,
    ): Double {
        val beat = outgoing.beatMs
        var error = incoming.beatOffsetMs(incomingPositionMs) - outgoing.beatOffsetMs(outgoingPositionMs)
        if (error > beat / 2) error -= beat
        if (error < -beat / 2) error += beat
        return error
    }
}
