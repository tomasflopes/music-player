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

    /** The first phrase start at or after [ms] (never before the first beat). */
    fun phraseStartAtOrAfter(ms: Double): Double {
        val phrase = ceil((ms - firstBeatMs) / phraseMs - EPSILON).coerceAtLeast(0.0)
        return firstBeatMs + phrase * phraseMs
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
        return if (unit < 0) null else (firstBeatMs + unit * unitMs).takeIf { it >= low - EPSILON_MS }
    }

    companion object {
        private const val MS_PER_MINUTE = 60_000.0
        private const val EPSILON = 1e-9
        private const val EPSILON_MS = 1e-6

        /** The grid of [profile]; [tempoMultiplier] 0.5 or 2 reads the same first beat at half or double tempo. */
        fun of(
            profile: TransitionProfile,
            config: TransitionConfig,
            tempoMultiplier: Double = 1.0,
        ) = BeatGrid(
            profile.firstBeatMs,
            profile.tempoBpm * tempoMultiplier,
            config.beatsPerBar,
            config.beatsPerPhrase,
        )
    }
}

/** The beat-phase error between two playing songs, which the Transition lab shows live (design phase 7). */
object BeatPhase {
    /**
     * How far into its beat the incoming song is minus how far into its beat the outgoing song is, in ms of real time,
     * wrapped into half a beat either way. Positive: the incoming beat came first (the incoming song is ahead).
     * Positions are file positions; [incomingSpeed] turns the incoming offset from file ms into real ms (1.0 when the
     * incoming song is not stretched). No allocation, so it can run on every tick.
     */
    fun errorMs(
        outgoing: BeatGrid,
        outgoingPositionMs: Double,
        incoming: BeatGrid,
        incomingPositionMs: Double,
        incomingSpeed: Double = 1.0,
    ): Double {
        val beat = outgoing.beatMs
        val incomingOffset = incoming.beatOffsetMs(incomingPositionMs) / incomingSpeed
        var error = incomingOffset - outgoing.beatOffsetMs(outgoingPositionMs)
        if (error > beat / 2) error -= beat
        if (error < -beat / 2) error += beat
        return error
    }
}
