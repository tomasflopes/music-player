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
)

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
)

/** Consecutive quick skips are "looking for something", not a verdict on the songs (design §7.4). */
data class BurstRule(
    val minSkips: Int = 3,
    val softening: Double = 0.1,
    /** Quick skips whose starts are further apart than this are separate occasions. */
    val maxGapMs: Long = 60_000,
)

data class DecayRule(
    val halfLifeDays: Double = 30.0,
    /** Older rows are not read: after 8 half-lives under 0.4 % of a weight is left. */
    val horizonDays: Int = 240,
) {
    val halfLifeMs: Double get() = halfLifeDays * MS_PER_DAY
    val horizonMs: Long get() = horizonDays * MS_PER_DAY.toLong()

    private companion object {
        const val MS_PER_DAY = 86_400_000.0
    }
}

data class TopListRule(
    val size: Int = 100,
    /** Only songs scoring above this are listed. */
    val minScore: Double = 0.0,
)
