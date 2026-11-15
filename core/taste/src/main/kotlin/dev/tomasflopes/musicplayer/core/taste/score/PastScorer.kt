package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig

/**
 * The song scores as they stood at a moment in the past (ADR 0064): the rows before that moment, decayed to that
 * moment, by the same [TasteScorer] that scores today. Nothing in the scorer changes: it already takes `now` as a
 * parameter, so a past moment is the same call with older rows only. A row at the moment itself is after it (the
 * quiet window of Rewind starts there), and rows older than the decay horizon are dropped, as `OnDemandScoreBoard`
 * does for today.
 */
class PastScorer(
    private val config: TasteConfig = TasteConfig(),
) {
    private val scorer = TasteScorer(config)

    /** The oldest row [songsAsOf] can use for [asOfEpochMs]; read the logs from here. */
    fun oldestRowFor(asOfEpochMs: Long): Long = asOfEpochMs - config.decay.horizonMs

    /** [signals] oldest first, as logged. Every song with a row in range is returned, best first. */
    fun songsAsOf(
        signals: TasteSignals,
        asOfEpochMs: Long,
    ): List<SongScore> {
        val range = oldestRowFor(asOfEpochMs) until asOfEpochMs
        val before =
            TasteSignals(
                plays = signals.plays.filter { it.startedAtEpochMs in range },
                playlistAdds = signals.playlistAdds.filter { it.atEpochMs in range },
                searches = signals.searches.filter { it.atEpochMs in range },
            )
        return scorer.scoreAll(before, emptyMap(), asOfEpochMs).songs
    }
}
