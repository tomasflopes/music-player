package dev.tomasflopes.musicplayer.core.analysis.config

/**
 * The numbers behind the sound vector (ADR 0087, which replaced 0068): which share of songs is clipped at each end
 * before scaling to 0..1, and how much each feature counts in the distance. Weights are applied inside the
 * vectorizer, so the distance between two vectors is plain Euclidean. Defaults are the owner's decision; retuning
 * them is a config change, not a profiler change.
 */
data class SoundVectorizerConfig(
    /** Values at or below this percentile of the library become 0. */
    val lowPercentile: Double = 1.0,
    /** Values at or above this percentile of the library become 1. */
    val highPercentile: Double = 99.0,
    val centroidWeight: Double = 1.0,
    val bassWeight: Double = 1.0,
    val onsetWeight: Double = 0.5,
    val beatStrengthWeight: Double = 0.8,
    val relativeDynamicsWeight: Double = 0.8,
    val flatnessWeight: Double = 0.6,
    val tempoWeight: Double = 0.4,
) {
    init {
        require(lowPercentile in 0.0..100.0) { "lowPercentile must be in 0..100: $lowPercentile" }
        require(highPercentile in 0.0..100.0) { "highPercentile must be in 0..100: $highPercentile" }
        require(lowPercentile < highPercentile) {
            "lowPercentile must be below highPercentile: $lowPercentile, $highPercentile"
        }
        weights().forEach { require(it.isFinite() && it >= 0.0) { "A weight must not be negative: $it" } }
    }

    /** The weights in vector order: centroid, bass, onset, beat strength, relative dynamics, flatness, tempo. */
    fun weights(): List<Double> =
        listOf(
            centroidWeight,
            bassWeight,
            onsetWeight,
            beatStrengthWeight,
            relativeDynamicsWeight,
            flatnessWeight,
            tempoWeight,
        )
}
