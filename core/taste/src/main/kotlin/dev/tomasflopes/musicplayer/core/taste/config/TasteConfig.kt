package dev.tomasflopes.musicplayer.core.taste.config

/**
 * Every tunable number behind the taste signals (design §7.4 and §13), in one place (ADR 0024). Defaults are the
 * starting values the owner approved; change them here and the scores follow, because nothing is stored.
 */
data class TasteConfig(
    val weights: SignalWeights = SignalWeights(),
    val burst: BurstRule = BurstRule(),
    val decay: DecayRule = DecayRule(),
    val top: TopListRule = TopListRule(),
    val artist: ArtistRule = ArtistRule(),
    val search: SearchRule = SearchRule(),
    /**
     * Share of a skip's weight that counts when the app chose the song (mix and radio rows, ADR 0084). 1.0 scores
     * them at full weight, as a mix's own score board does. Only negative contributions are scaled.
     */
    val skipShare: Double = 0.3,
) {
    init {
        require(skipShare.isFinite() && skipShare in 0.0..1.0) { "skipShare must be in 0..1: $skipShare" }
    }
}

/** What one play is worth. Skips are negative; the fractions are of the song's duration. */
data class SignalWeights(
    /** The owner chose this song (tap or search), unless they skipped it inside [earlySkipMs]. */
    val picked: Double = 3.0,
    /** Played to the end under shuffle or in a mix. */
    val completed: Double = 1.0,
    val skipEarly: Double = -2.0,
    val skipMid: Double = -1.0,
    val skipLate: Double = 0.0,
    val earlySkipMs: Long = 15_000,
    val midSkipFraction: Double = 0.5,
    /** A play strictly past this fraction counts as completed, whatever ended it. */
    val completedFraction: Double = 0.8,
    /** The owner put the song in a playlist (design §7.4). */
    val addedToPlaylist: Double = 3.0,
) {
    init {
        require(listOf(picked, completed, skipEarly, skipMid, skipLate, addedToPlaylist).all { it.isFinite() }) {
            "signal weights must be finite"
        }
        require(earlySkipMs >= 0) { "earlySkipMs must not be negative: $earlySkipMs" }
        require(midSkipFraction in 0.0..1.0) { "midSkipFraction must be in 0..1: $midSkipFraction" }
        require(completedFraction in 0.0..1.0) { "completedFraction must be in 0..1: $completedFraction" }
        require(midSkipFraction <= completedFraction) { "midSkipFraction must not exceed completedFraction" }
    }
}

/** Consecutive quick skips are "looking for something", not a verdict on the songs (design §7.4). */
data class BurstRule(
    val minSkips: Int = 3,
    val softening: Double = 0.1,
    /** Quick skips whose starts are further apart than this are separate occasions. */
    val maxGapMs: Long = 60_000,
) {
    init {
        require(minSkips >= 1) { "minSkips must be at least 1: $minSkips" }
        require(softening in 0.0..1.0) { "softening must be in 0..1: $softening" }
        require(maxGapMs >= 0) { "maxGapMs must not be negative: $maxGapMs" }
    }
}

data class DecayRule(
    val halfLifeDays: Double = 30.0,
    /** Older rows are not read: after 8 half-lives under 0.4 % of a weight is left. */
    val horizonDays: Int = 240,
) {
    init {
        require(halfLifeDays.isFinite() && halfLifeDays > 0) { "halfLifeDays must be positive: $halfLifeDays" }
        require(horizonDays > 0) { "horizonDays must be positive: $horizonDays" }
    }

    val halfLifeMs: Double get() = halfLifeDays * MS_PER_DAY
    val horizonMs: Long get() = horizonDays * MS_PER_DAY.toLong()

    private companion object {
        const val MS_PER_DAY = 86_400_000.0
    }
}

data class TopListRule(
    val size: Int = 100,
    /** Only songs scoring above this are listed; above 0, so sums that cancel up to rounding stay out. */
    val minScore: Double = 1e-9,
) {
    init {
        require(size >= 1) { "size must be at least 1: $size" }
        require(minScore.isFinite()) { "minScore must be finite: $minScore" }
    }
}

/** How song signals roll up to artists, and the signals that belong to an artist only (design §7.4, ADR 0049). */
data class ArtistRule(
    /** Featured artists get this share of what a song earns; the main artist gets all of it. */
    val featuredShare: Double = 0.3,
    /** The owner started an artist shuffle: this goes to the artist, on top of what the songs earn. */
    val shuffleStarted: Double = 2.0,
    /** Artist-shuffle rows whose starts are further apart than this are separate shuffles. */
    val shuffleGapMs: Long = 30 * 60_000,
) {
    init {
        require(featuredShare in 0.0..1.0) { "featuredShare must be in 0..1: $featuredShare" }
        require(shuffleStarted.isFinite()) { "shuffleStarted must be finite: $shuffleStarted" }
        require(shuffleGapMs >= 0) { "shuffleGapMs must not be negative: $shuffleGapMs" }
    }
}

/**
 * What choosing a search result earns on its own. The play a song result leads to is already a picked row (+3), so
 * [song] stays 0 to avoid counting it twice; an artist result opens a page and may never play, so it counts for
 * the artist. Album results carry no signal: what they play is logged as plays.
 */
data class SearchRule(
    val song: Double = 0.0,
    val artist: Double = 1.0,
) {
    init {
        require(song.isFinite() && artist.isFinite()) { "search weights must be finite" }
    }
}
