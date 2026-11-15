package dev.tomasflopes.musicplayer.core.analysis

/**
 * What the profiler stores for one song (ADR 0065). [fixed] is the stored layout, in order; [energyCurve] is the
 * variable-length tail, one RMS value per second. Everything is raw: normalising to 0..1 is phase 4's job.
 */
class SoundProfile(
    val fixed: FloatArray,
    val energyCurve: FloatArray,
) {
    init {
        require(fixed.size == FIXED_SIZE) { "A profile has $FIXED_SIZE fixed values, not ${fixed.size}" }
    }

    /** The 8 v1 features, in their stored order. */
    val features: FloatArray get() = fixed.copyOfRange(0, FEATURE_COUNT)

    val tempoBpm: Float get() = fixed[TEMPO]
    val meanLoudness: Float get() = fixed[MEAN_LOUDNESS]
    val loudnessVariation: Float get() = fixed[LOUDNESS_VARIATION]
    val spectralCentroidHz: Float get() = fixed[SPECTRAL_CENTROID]
    val bassEnergyRatio: Float get() = fixed[BASS_RATIO]
    val onsetDensity: Float get() = fixed[ONSET_DENSITY]
    val beatStrength: Float get() = fixed[BEAT_STRENGTH]
    val zeroCrossingRate: Float get() = fixed[ZERO_CROSSING_RATE]
    val firstBeatSeconds: Float get() = fixed[FIRST_BEAT]
    val beatTempoBpm: Float get() = fixed[BEAT_TEMPO]
    val tempoConfidence: Float get() = fixed[TEMPO_CONFIDENCE]
    val musicStartSeconds: Float get() = fixed[MUSIC_START]
    val introEndSeconds: Float get() = fixed[INTRO_END]
    val outroStartSeconds: Float get() = fixed[OUTRO_START]
    val musicEndSeconds: Float get() = fixed[MUSIC_END]

    /** The 12 raw chroma bins, C first. */
    val chroma: FloatArray get() = fixed.copyOfRange(CHROMA_FIRST, CHROMA_FIRST + CHROMA_BINS)

    /** Tempo candidate [slot] (0 is the best): BPM and score relative to the best; 0 and 0 when absent. */
    fun candidate(slot: Int): Pair<Float, Float> = fixed[CANDIDATES + 2 * slot] to fixed[CANDIDATES + 2 * slot + 1]

    override fun equals(other: Any?): Boolean =
        other is SoundProfile && fixed.contentEquals(other.fixed) && energyCurve.contentEquals(other.energyCurve)

    override fun hashCode(): Int = 31 * fixed.contentHashCode() + energyCurve.contentHashCode()

    companion object {
        const val FEATURE_COUNT = 8
        const val CANDIDATE_SLOTS = 3
        const val CHROMA_BINS = 12

        const val TEMPO = 0
        const val MEAN_LOUDNESS = 1
        const val LOUDNESS_VARIATION = 2
        const val SPECTRAL_CENTROID = 3
        const val BASS_RATIO = 4
        const val ONSET_DENSITY = 5
        const val BEAT_STRENGTH = 6
        const val ZERO_CROSSING_RATE = 7
        const val FIRST_BEAT = 8
        const val BEAT_TEMPO = 9
        const val TEMPO_CONFIDENCE = 10
        const val CANDIDATES = 11
        const val CHROMA_FIRST = CANDIDATES + 2 * CANDIDATE_SLOTS
        const val MUSIC_START = CHROMA_FIRST + CHROMA_BINS
        const val INTRO_END = MUSIC_START + 1
        const val OUTRO_START = MUSIC_START + 2
        const val MUSIC_END = MUSIC_START + 3
        const val FIXED_SIZE = MUSIC_END + 1

        /** Names of the fixed values, in stored order (the export header, ADR 0065). */
        val FIXED_NAMES: List<String> =
            listOf(
                "tempo_bpm",
                "mean_loudness",
                "loudness_variation",
                "spectral_centroid_hz",
                "bass_energy_ratio",
                "onset_density",
                "beat_strength",
                "zero_crossing_rate",
                "first_beat_s",
                "beat_tempo_bpm",
                "tempo_confidence",
            ) +
                (1..CANDIDATE_SLOTS).flatMap { listOf("tempo_candidate_${it}_bpm", "tempo_candidate_${it}_score") } +
                listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B").map { "chroma_$it" } +
                listOf("music_start_s", "intro_end_s", "outro_start_s", "music_end_s")
    }
}
