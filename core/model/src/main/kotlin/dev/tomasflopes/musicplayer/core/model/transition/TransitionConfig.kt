package dev.tomasflopes.musicplayer.core.model.transition

/**
 * Every number of the beat-matched transitions (design phase 7, ADR 0092), with the spec defaults. None of these is a
 * setting: Settings only holds the style. Slices 2 to 4 read the fields they own; slice 1 reads the beat-match,
 * overlap and preload ones.
 */
@Suppress("LongParameterList")
data class TransitionConfig(
    /** Both songs need a tempo confidence of at least this much for a beat match (calibration is still open). */
    val minConfidence: Float = 0.5f,
    /** The largest stretch of the incoming song, as |r - 1| (slice 2). */
    val maxStretch: Double = 0.08,
    /** Slice 1 does not stretch, so it only matches tempos this close: the incoming song stays at speed 1.0. */
    val unstretchedTolerance: Double = 0.015,
    val beatsPerBar: Int = 4,
    val beatsPerPhrase: Int = 16,
    val overlapBars: Int = 8,
    val minOverlapBars: Int = 4,
    val maxOverlapMs: Long = 20_000,
    val easeBars: Int = 8,
    val maxSpeedStep: Double = 0.005,
    val maxNudge: Double = 0.005,
    val maxPhaseErrorMs: Long = 40,
    val bassCutHz: Int = 150,
    val bassCutDb: Float = DEFAULT_BASS_CUT_DB,
    val bassSwapBeats: Int = 2,
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
