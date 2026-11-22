package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.core.model.transition.TransitionProfile
import kotlin.math.roundToLong

private const val MS_PER_SECOND = 1_000.0

/** Reads what a beat-synced transition needs out of the 33 fixed floats of a stored profile (ADR 0065, 0092). */
object TransitionProfiles {
    /**
     * The grid, the confidence, the tempo candidates and the four boundaries, in ms. Returns null when the numbers
     * cannot be a beat grid (not finite, a negative time, no tempo, boundaries out of order); a transition then falls
     * back to the crossfade.
     */
    fun fromFixed(fixed: FloatArray): TransitionProfile? {
        val profile = SoundProfile(fixed, FloatArray(0))
        val candidates =
            (0 until SoundProfile.CANDIDATE_SLOTS)
                .map { profile.candidate(it).first.toDouble() }
                .filter { it > 0.0 }
        return TransitionProfile(
            tempoBpm = profile.beatTempoBpm.toDouble(),
            firstBeatMs = profile.firstBeatSeconds * MS_PER_SECOND,
            tempoConfidence = profile.tempoConfidence,
            musicStartMs = ms(profile.musicStartSeconds),
            introEndMs = ms(profile.introEndSeconds),
            outroStartMs = ms(profile.outroStartSeconds),
            musicEndMs = ms(profile.musicEndSeconds),
            candidateBpms = candidates,
        ).takeIf { it.isUsable }
    }

    private fun ms(seconds: Float): Long = if (seconds.isFinite()) (seconds * MS_PER_SECOND).roundToLong() else -1L
}
