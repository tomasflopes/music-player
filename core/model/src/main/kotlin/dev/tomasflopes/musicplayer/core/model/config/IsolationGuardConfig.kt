package dev.tomasflopes.musicplayer.core.model.config

/**
 * The numbers behind the outlier guard (ADR 0087). A seed is isolated when its [rank]-th nearest distance is above
 * the [thresholdPercentile] of that same distance over up to [sampleSize] evenly spaced songs of the space. An
 * isolated seed gives [artistShare] of the neighbour slots to songs of its own artist.
 */
data class IsolationGuardConfig(
    val rank: Int = 10,
    val thresholdPercentile: Double = 90.0,
    val sampleSize: Int = 1_500,
    val artistShare: Double = 0.3,
) {
    init {
        require(rank >= 1) { "rank must be at least 1: $rank" }
        require(thresholdPercentile in 0.0..100.0) { "thresholdPercentile must be in 0..100: $thresholdPercentile" }
        require(sampleSize >= 1) { "sampleSize must be at least 1: $sampleSize" }
        require(artistShare in 0.0..1.0) { "artistShare must be in 0..1: $artistShare" }
    }
}
