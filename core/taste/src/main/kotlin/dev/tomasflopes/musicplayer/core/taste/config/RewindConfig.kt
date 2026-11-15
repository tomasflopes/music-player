package dev.tomasflopes.musicplayer.core.taste.config

/**
 * The numbers behind the log-only Rewind list (design §7.7, ADR 0064), in one place like [DiscoverConfig].
 * Defaults are starting values the owner can retune.
 */
data class RewindConfig(
    /** A song must have no play in this many days, and it is scored as it stood this many days ago. */
    val quietDays: Int = 60,
    /**
     * A song counts as "loved then" when its score at the start of the quiet window is at least this. With the
     * default weights that is one picked play within about 13 days before the window, or two within 40 days.
     */
    val minScoreThen: Double = 2.0,
    /** How many songs the list holds at most. */
    val size: Int = 50,
    /** No more than this many songs by one main artist, so one old favourite cannot fill the list. */
    val maxPerArtist: Int = 3,
    /** No song by the same main artist among the next [artistGap] songs; same meaning as smart shuffle's gap. */
    val artistGap: Int = SmartShuffleConfig().artistGap,
    /** The past score is squashed with `tanh(score / tasteScale)`, so one huge score cannot bury the rest. */
    val tasteScale: Double = 6.0,
    /** How much the random part can move a song, in the same -1..1 units as the squashed score. */
    val randomWeight: Double = 0.3,
) {
    init {
        require(quietDays >= 1) { "quietDays must be at least 1: $quietDays" }
        require(minScoreThen.isFinite()) { "minScoreThen must be finite: $minScoreThen" }
        require(size >= 1) { "size must be at least 1: $size" }
        require(maxPerArtist >= 1) { "maxPerArtist must be at least 1: $maxPerArtist" }
        require(artistGap in 0..SmartShuffleConfig.MAX_ARTIST_GAP) {
            "artistGap must be in 0..${SmartShuffleConfig.MAX_ARTIST_GAP}: $artistGap"
        }
        require(tasteScale.isFinite() && tasteScale > 0.0) { "tasteScale must be positive: $tasteScale" }
        require(randomWeight.isFinite() && randomWeight >= 0.0) { "randomWeight must not be negative: $randomWeight" }
    }

    val quietMs: Long get() = quietDays * MS_PER_DAY

    private companion object {
        const val MS_PER_DAY = 86_400_000L
    }
}
