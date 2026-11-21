package dev.tomasflopes.musicplayer.core.taste.config

/**
 * Every number behind the mix engine (design §7.6, ADR 0076, 0085), in one place like [RadioConfig]. Defaults are
 * starting values the owner can retune.
 */
data class MixConfig(
    /** Songs in one batch. */
    val batchSize: Int = 25,
    /**
     * Share of the batch that is exploration, drawn from the [explorationBand] nearest candidates. A mix with no
     * feedback starts here; with feedback it falls towards [explorationFloor] (see [explorationShareFor]).
     */
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
    /** A refill starts when this many non-manual songs or fewer are left in the queue; below [batchSize] (ADR 0078). */
    val refillLowWater: Int = 5,
    /** A song needs this mix score (its own rows, skips at full weight) to count as liked (ADR 0085). */
    val likedMinScore: Double = 0.5,
    /** At most this many liked songs become anchors, after the gate; the best score first, then the most recent. */
    val likedMax: Int = 15,
    /**
     * R, the liked-anchor gate: a liked song becomes an anchor only if its rank key against the seed-only anchors is at
     * most this (the key is in units of an anchor's reach). Chosen from the owner's library in ADR 0085.
     */
    val likedGate: Double = 1.5,
    /** A song with a mix score at or below this is left out of the mix until its score decays (ADR 0085). */
    val bannedScore: Double = -3.0,
    /** A song played in this mix in the last [mixRepeatDays] days gets [mixRepeatPenalty] added to its rank key. */
    val mixRepeatDays: Int = 14,
    val mixRepeatPenalty: Double = 1.0,
    /** Exploration never falls below this share once the mix has [explorationRows] informative rows. */
    val explorationFloor: Double = 0.10,
    /** Informative rows (not neutral, not burst-softened) it takes to go from [explorationShare] to the floor. */
    val explorationRows: Int = 200,
    /** Fixes the exploration draw when set (tests, debugging); null draws a new one for every batch. */
    val seed: Long? = null,
) {
    init {
        require(batchSize >= 1) { "batchSize must be at least 1: $batchSize" }
        require(explorationBand >= batchSize) { "explorationBand must hold a batch: $explorationBand < $batchSize" }
        require(openingPerArtist in 0..maxPerArtist) {
            "openingPerArtist must be in 0..$maxPerArtist: $openingPerArtist"
        }
        require(refillLowWater in 0 until batchSize) {
            "refillLowWater must be in 0 until $batchSize: $refillLowWater"
        }
        require(densityRank >= 1) { "densityRank must be at least 1: $densityRank" }
        require(explorationShare.isFinite() && explorationShare in 0.0..1.0) {
            "explorationShare must be in 0..1: $explorationShare"
        }
        require(likedMinScore.isFinite() && likedMinScore > 0.0) { "likedMinScore must be above 0: $likedMinScore" }
        require(likedMax >= 0) { "likedMax must not be negative: $likedMax" }
        require(likedGate.isFinite() && likedGate > 0.0) { "likedGate must be above 0: $likedGate" }
        require(bannedScore.isFinite() && bannedScore < 0.0) { "bannedScore must be below 0: $bannedScore" }
        require(mixRepeatDays >= 0) { "mixRepeatDays must not be negative: $mixRepeatDays" }
        require(mixRepeatPenalty.isFinite() && mixRepeatPenalty >= 0.0) {
            "mixRepeatPenalty must not be negative: $mixRepeatPenalty"
        }
        require(explorationFloor.isFinite() && explorationFloor in 0.0..1.0) {
            "explorationFloor must be in 0..1: $explorationFloor"
        }
        require(explorationRows >= 1) { "explorationRows must be at least 1: $explorationRows" }
    }

    /**
     * The exploration share of a batch for a mix with [informativeRows] rows that said something: [explorationShare]
     * with none, falling in a straight line to [explorationFloor] at [explorationRows] and staying there. It never
     * rises above [explorationShare], so a mix configured with less than the floor keeps what it has.
     */
    fun explorationShareFor(informativeRows: Int): Double {
        val low = minOf(explorationFloor, explorationShare)
        val done = informativeRows.coerceIn(0, explorationRows)
        return explorationShare - (explorationShare - low) * done / explorationRows
    }

    /**
     * The builder config this mix batch runs with; the builder is shared with the radio (ADR 0072, 0075). A batch
     * passes its own [explorationShare] from [explorationShareFor].
     */
    fun builderConfig(explorationShare: Double = this.explorationShare): RadioConfig =
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
