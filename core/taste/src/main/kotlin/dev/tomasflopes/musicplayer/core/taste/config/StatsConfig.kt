package dev.tomasflopes.musicplayer.core.taste.config

/** The numbers behind the Wrapped cards (design §7.10, ADR 0088), in one place like [TasteConfig] (ADR 0024). */
data class StatsConfig(
    /** How many entries a top list or a "first plays" list keeps. */
    val topSize: Int = 5,
    /** A song needs this many real skips in the period to be listed as most skipped. */
    val minSkips: Int = 2,
    /** An artist's plays must rise by at least this much against the previous period to be the comeback. */
    val comebackMinRise: Int = 2,
    /**
     * A period is closed, and cached for the life of the process, this long after it ends. The wait covers a play
     * that started before midnight and was logged after it.
     */
    val closedAfterMs: Long = 60 * 60 * 1000,
) {
    init {
        require(topSize >= 1) { "topSize must be at least 1: $topSize" }
        require(minSkips >= 1) { "minSkips must be at least 1: $minSkips" }
        require(comebackMinRise >= 1) { "comebackMinRise must be at least 1: $comebackMinRise" }
        require(closedAfterMs >= 0) { "closedAfterMs must not be negative: $closedAfterMs" }
    }
}
