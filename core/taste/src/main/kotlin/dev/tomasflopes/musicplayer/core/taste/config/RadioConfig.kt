package dev.tomasflopes.musicplayer.core.taste.config

import kotlin.math.abs

/**
 * The numbers behind Radio v0 and the artist radio (design §7.6, ADR 0072, exploration changed by ADR 0075), in one
 * place like [DiscoverConfig]. Defaults are starting values the owner can retune.
 */
data class RadioConfig(
    /** Songs in one batch, the seed included. */
    val batchSize: Int = 25,
    /** Share of the songs after the seed that are the seed's nearest neighbours. */
    val nearestShare: Double = 0.88,
    /** Share of the songs after the seed that are exploration picks (ADR 0075: 12 %, was 30 % in ADR 0072). */
    val explorationShare: Double = 0.12,
    /**
     * Exploration picks are drawn at random only from the [explorationBand] nearest candidates (ranked as for the
     * nearest part), never from the whole library. A wider band is a looser radio; it must hold a whole batch.
     */
    val explorationBand: Int = 150,
    /** A song played in the last [recentDays] days gets [recentPenalty] added to its distance. */
    val recentDays: Int = 2,
    /** Added to the distance of a recently played song, in the units of the sound space. */
    val recentPenalty: Double = 0.1,
    /** No more than this many songs by one main artist in a batch, the seed counted. */
    val maxPerArtist: Int = 3,
    /** No song by the same main artist among the next [artistGap] songs; same meaning as smart shuffle's gap. */
    val artistGap: Int = SmartShuffleConfig().artistGap,
    /**
     * Shuffle the finished batch of an artist radio before it is queued (ADR 0086). The song radio ignores it: its
     * seed plays first by definition (ADR 0072). False keeps the builder's order.
     */
    val shuffleBatch: Boolean = true,
    /** Fixes the exploration draw when set (tests, debugging); null draws a new one for every radio. */
    val seed: Long? = null,
) {
    init {
        require(batchSize >= 1) { "batchSize must be at least 1: $batchSize" }
        require(nearestShare.isFinite() && nearestShare >= 0.0) { "nearestShare must not be negative: $nearestShare" }
        require(explorationShare.isFinite() && explorationShare >= 0.0) {
            "explorationShare must not be negative: $explorationShare"
        }
        require(abs(nearestShare + explorationShare - 1.0) < SHARE_EPSILON) {
            "nearestShare and explorationShare must add up to 1: $nearestShare + $explorationShare"
        }
        require(explorationBand >= batchSize) {
            "explorationBand must hold a whole batch ($batchSize): $explorationBand"
        }
        require(recentDays >= 0) { "recentDays must not be negative: $recentDays" }
        require(recentPenalty.isFinite() && recentPenalty >= 0.0) {
            "recentPenalty must not be negative: $recentPenalty"
        }
        require(maxPerArtist >= 1) { "maxPerArtist must be at least 1: $maxPerArtist" }
        require(artistGap in 0..SmartShuffleConfig.MAX_ARTIST_GAP) {
            "artistGap must be in 0..${SmartShuffleConfig.MAX_ARTIST_GAP}: $artistGap"
        }
    }

    private companion object {
        const val SHARE_EPSILON = 1e-9
    }
}
