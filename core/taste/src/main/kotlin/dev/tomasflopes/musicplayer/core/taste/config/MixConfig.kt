package dev.tomasflopes.musicplayer.core.taste.config

/**
 * Every number behind the mix engine (design §7.6, ADR 0076), in one place like [RadioConfig]. Defaults are starting
 * values the owner can retune. Slice 1 has a fixed exploration share; the schedule that lowers it comes later.
 */
data class MixConfig(
    /** Songs in one batch. */
    val batchSize: Int = 25,
    /** Share of the batch that is exploration, drawn from the [explorationBand] nearest candidates. */
    val explorationShare: Double = 0.30,
    /** Exploration picks come only from this many nearest candidates; it must hold a whole batch. */
    val explorationBand: Int = 600,
    /**
     * An anchor's reach is the distance to its [densityRank]th nearest song, so a crowded sound and a sparse one
     * count alike (the rank key is the distance divided by that reach).
     */
    val densityRank: Int = 50,
    /** A song played in the last [recentDays] days gets [recentPenalty] added to its rank key. */
    val recentDays: Int = 2,
    /** Added to the rank key of a recently played song; the key is in units of an anchor's reach. */
    val recentPenalty: Double = 0.1,
    /** No more than this many songs by one main artist in a batch. */
    val maxPerArtist: Int = 3,
    /** No song by the same main artist among the next [artistGap] songs; same meaning as smart shuffle's gap. */
    val artistGap: Int = SmartShuffleConfig().artistGap,
    /**
     * For every artist seed, this many of the artist's own songs (the closest to its sound) open the batch, so a mix
     * always plays its seed artists; 0 turns it off. They count towards [maxPerArtist].
     */
    val openingPerArtist: Int = 2,
    /** Fixes the exploration draw when set (tests, debugging); null draws a new one for every batch. */
    val seed: Long? = null,
) {
    init {
        require(batchSize >= 1) { "batchSize must be at least 1: $batchSize" }
        require(explorationBand >= batchSize) { "explorationBand must hold a batch: $explorationBand < $batchSize" }
        require(openingPerArtist in 0..maxPerArtist) {
            "openingPerArtist must be in 0..$maxPerArtist: $openingPerArtist"
        }
        require(densityRank >= 1) { "densityRank must be at least 1: $densityRank" }
        require(explorationShare.isFinite() && explorationShare in 0.0..1.0) {
            "explorationShare must be in 0..1: $explorationShare"
        }
    }

    /** The builder config this mix batch runs with; the builder is shared with the radio (ADR 0072, 0075). */
    fun builderConfig(): RadioConfig =
        RadioConfig(
            batchSize = batchSize,
            nearestShare = 1.0 - explorationShare,
            explorationShare = explorationShare,
            explorationBand = explorationBand,
            recentDays = recentDays,
            recentPenalty = recentPenalty,
            maxPerArtist = maxPerArtist,
            artistGap = artistGap,
            seed = seed,
        )
}
