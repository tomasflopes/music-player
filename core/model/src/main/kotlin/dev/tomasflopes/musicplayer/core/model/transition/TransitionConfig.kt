package dev.tomasflopes.musicplayer.core.model.transition

/**
 * Every number of the beat-matched transitions (design phase 7, ADR 0092), with the spec defaults. None of these is a
 * setting: Settings only holds the style. Slices 3 and 4 read the fields they own; slices 1 and 2 read the beat-match,
 * stretch, ease-back, phase lock, overlap and preload ones.
 */
@Suppress("LongParameterList")
data class TransitionConfig(
    /** The tempo confidence [StoredConfidenceReliability] asks for: the slice 1 and 2 guess, kept for comparison. */
    val minConfidence: Float = 0.5f,
    /** The tempo confidence [CalibratedConfidenceReliability] asks for, from the owner's checks (ADR 0099). */
    val calibratedMinConfidence: Float = 0.15f,
    /** The largest stretch of the incoming song, as |r - 1|, half and double tempo included (ADR 0094). */
    val maxStretch: Double = 0.08,
    val beatsPerBar: Int = 4,
    val beatsPerPhrase: Int = 16,
    val overlapBars: Int = 8,
    val minOverlapBars: Int = 4,
    val maxOverlapMs: Long = 20_000,
    val easeBars: Int = 4,
    val maxSpeedStep: Double = 0.01,
    /** True: the speed returns to 1.0 in one step at the next phrase start instead of in bar steps (ADR 0095). */
    val singleStepEaseBack: Boolean = false,
    val maxNudge: Double = 0.005,
    val maxPhaseErrorMs: Long = 40,
    /** Phase lock nudges the incoming speed during an overlap (ADR 0096); off until the lab numbers are in. */
    val phaseLockEnabled: Boolean = false,
    /** The beat-phase error has to stay above [maxPhaseErrorMs] this many bars before the beat match is abandoned. */
    val phaseLockAbandonBars: Int = 2,
    /** How often phase lock reads the error and may nudge the speed. */
    val phaseLockTickMs: Long = 250,
    val bassCutHz: Int = 150,
    val bassCutDb: Float = DEFAULT_BASS_CUT_DB,
    val bassSwapBeats: Int = 2,
    /** On for the owner's listening test. The rule over-flags on the real library (ADR 0106). */
    val talkEnabled: Boolean = true,
    val talkMinMs: Long = 8_000,
    val talkEnergyRatio: Float = 0.5f,
    val talkLeadMs: Long = 1_000,
    val talkDuckDb: Float = DEFAULT_TALK_DUCK_DB,
    val talkFadeBars: Int = 4,
    /** The lab starts this long before the switch point. */
    val labLeadMs: Long = 15_000,
    /** The next song is handed to the spare player this long before the overlap starts. */
    val preloadLeadMs: Long = 8_000,
) {
    private companion object {
        const val DEFAULT_BASS_CUT_DB = -24f
        const val DEFAULT_TALK_DUCK_DB = -6f
    }
}
