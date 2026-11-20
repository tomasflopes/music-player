package dev.tomasflopes.musicplayer.core.taste.config

/**
 * The numbers behind the solo-song extension (design docs/plans/2026-11-20-refill-design.md, ADR 0080): a single song
 * chosen from Search is followed by a batch of songs that sound like it, refilled the same way when it runs low.
 */
data class SoloExtensionConfig(
    /** Songs appended after the chosen one, in the first extension and in every refill. */
    val batchSize: Int = 5,
    /** A refill starts when this many non-manual songs or fewer are left; it must be below [batchSize]. */
    val lowWater: Int = 2,
    /** Share of a batch drawn as exploration from the nearest songs (same band rule as the radio, ADR 0075). */
    val explorationShare: Double = RadioConfig().explorationShare,
) {
    init {
        require(batchSize >= 1) { "batchSize must be at least 1: $batchSize" }
        require(lowWater in 0 until batchSize) { "lowWater must be in 0 until $batchSize: $lowWater" }
        require(explorationShare.isFinite() && explorationShare in 0.0..1.0) {
            "explorationShare must be in 0..1: $explorationShare"
        }
    }
}
