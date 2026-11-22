package dev.tomasflopes.musicplayer.core.model.transition

/**
 * What a transition needs from a stored sound profile (ADR 0065), in milliseconds and BPM, with no Android and no
 * analysis types. [firstBeatMs] and [tempoBpm] are the beat grid; the four boundaries are music start, intro end,
 * outro start and music end. [candidateBpms] are the tempo candidates, best first, for the half and double tempo
 * matches of slice 2.
 */
data class TransitionProfile(
    val tempoBpm: Double,
    val firstBeatMs: Double,
    val tempoConfidence: Float,
    val musicStartMs: Long,
    val introEndMs: Long,
    val outroStartMs: Long,
    val musicEndMs: Long,
    val candidateBpms: List<Double> = emptyList(),
) {
    /** Finite numbers in a sane range, boundaries in order: anything else is treated as "no profile". */
    val isUsable: Boolean
        get() =
            tempoBpm.isFinite() &&
                tempoBpm in MIN_BPM..MAX_BPM &&
                firstBeatMs.isFinite() &&
                firstBeatMs >= 0.0 &&
                !tempoConfidence.isNaN() &&
                musicStartMs in 0..introEndMs &&
                introEndMs <= outroStartMs &&
                outroStartMs <= musicEndMs

    private companion object {
        const val MIN_BPM = 30.0
        const val MAX_BPM = 300.0
    }
}

/** The stored profile of a song for the transitions, or null when it has none for the current profiler version. */
fun interface TransitionProfileSource {
    suspend fun profile(songId: Long): TransitionProfile?
}
