package dev.tomasflopes.musicplayer.core.taste.config

/**
 * The numbers behind the log-only Discover list (design §7.7, ADR 0063), in one place like [SmartShuffleConfig].
 * Defaults are starting values the owner can retune.
 */
data class DiscoverConfig(
    /** How many songs the list holds at most. */
    val size: Int = 50,
    /** No more than this many songs by one main artist, so one liked artist cannot fill the list. */
    val maxPerArtist: Int = 3,
    /** No song by the same main artist among the next [artistGap] songs; same meaning as smart shuffle's gap. */
    val artistGap: Int = SmartShuffleConfig().artistGap,
    /** An artist's score is squashed with `tanh(score / tasteScale)`, so one huge score cannot bury the rest. */
    val tasteScale: Double = 6.0,
    /** How much the random part can move a song, in the same -1..1 units as the squashed taste. */
    val randomWeight: Double = 0.5,
    /**
     * Weight of the artist's squashed taste score in the sound-aware rank (the `a` of ADR 0071). This and the
     * numbers below belong to the sound-aware rule; the artist-only rule above ignores them.
     */
    val artistWeight: Double = 1.0,
    /** Weight of the sound term in the sound-aware rank (the `s`). */
    val soundWeight: Double = 1.0,
    /** Weight of the random part in the sound-aware rank (the `r`); the artist-only rule keeps [randomWeight]. */
    val soundRandomWeight: Double = 0.4,
    /** How many anchors (liked songs spread over different sounds) the sound term measures against. */
    val anchorCount: Int = 25,
    /** The anchors are chosen from this many best-scored liked songs. */
    val anchorPool: Int = 100,
    /** An anchor's reach is the distance to its [densityRank]th nearest song (same meaning as the mix engine). */
    val densityRank: Int = 50,
    /** The rank key (distance over reach) at which the sound term is 0, the same as a song with no profile. */
    val neutralKey: Double = 1.0,
    /** Share of the list drawn at random from the [explorationBand] candidates nearest to an anchor. */
    val explorationShare: Double = 0.10,
    /** Exploration comes only from this many candidates nearest to an anchor; it must hold a whole list. */
    val explorationBand: Int = 150,
) {
    init {
        require(size >= 1) { "size must be at least 1: $size" }
        require(maxPerArtist >= 1) { "maxPerArtist must be at least 1: $maxPerArtist" }
        require(artistGap in 0..SmartShuffleConfig.MAX_ARTIST_GAP) {
            "artistGap must be in 0..${SmartShuffleConfig.MAX_ARTIST_GAP}: $artistGap"
        }
        require(tasteScale.isFinite() && tasteScale > 0.0) { "tasteScale must be positive: $tasteScale" }
        require(randomWeight.isFinite() && randomWeight >= 0.0) { "randomWeight must not be negative: $randomWeight" }
        require(artistWeight.isFinite() && artistWeight >= 0.0) { "artistWeight must not be negative: $artistWeight" }
        require(soundWeight.isFinite() && soundWeight >= 0.0) { "soundWeight must not be negative: $soundWeight" }
        require(soundRandomWeight.isFinite() && soundRandomWeight >= 0.0) {
            "soundRandomWeight must not be negative: $soundRandomWeight"
        }
        require(anchorCount >= 1) { "anchorCount must be at least 1: $anchorCount" }
        require(anchorPool >= anchorCount) { "anchorPool must hold the anchors: $anchorPool < $anchorCount" }
        require(densityRank >= 1) { "densityRank must be at least 1: $densityRank" }
        require(neutralKey.isFinite() && neutralKey >= 0.0) { "neutralKey must not be negative: $neutralKey" }
        require(explorationShare.isFinite() && explorationShare in 0.0..1.0) {
            "explorationShare must be in 0..1: $explorationShare"
        }
        require(explorationBand >= size) { "explorationBand must hold a list: $explorationBand < $size" }
    }
}
