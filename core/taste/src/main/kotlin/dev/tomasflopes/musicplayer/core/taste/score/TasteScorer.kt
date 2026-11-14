package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig

/**
 * The global taste score of every song in a stretch of the listening log (design §7.4). Pure: the result depends
 * only on the rows and on `now`, so changing a weight or the half-life and scoring again is the whole "rebuild".
 */
class TasteScorer(
    config: TasteConfig = TasteConfig(),
) {
    private val rules = SignalRules(config.weights)
    private val bursts = BrowsingBursts(config.burst)
    private val decay = Decay(config.decay)
    private val softening = config.burst.softening

    /** [events] oldest first, as logged. Every song with a row is returned, best first. */
    fun score(
        events: List<PlayEvent>,
        nowEpochMs: Long,
    ): List<SongScore> {
        val outcomes = events.map(rules::outcomeOf)
        val softened = bursts.softened(events, outcomes)
        val tallies = HashMap<SongId, Tally>()
        events.forEachIndexed { index, event ->
            val tally = tallies.getOrPut(event.songId) { Tally() }
            val weight = rules.weightOf(event, outcomes[index]) * if (softened[index]) softening else 1.0
            // A completed play forgives the skips before it (design §7.4); the +1 is added after.
            if (outcomes[index] == PlayOutcome.COMPLETED) tally.negative = 0.0
            val contribution = weight * decay.factor(nowEpochMs - event.startedAtEpochMs)
            if (contribution < 0) tally.negative += contribution else tally.positive += contribution
            tally.lastPlayedAtEpochMs = maxOf(tally.lastPlayedAtEpochMs, event.startedAtEpochMs)
        }
        return tallies
            .map { (songId, tally) -> SongScore(songId, tally.positive + tally.negative, tally.lastPlayedAtEpochMs) }
            .sortedWith(RANKING)
    }

    private class Tally(
        var positive: Double = 0.0,
        var negative: Double = 0.0,
        var lastPlayedAtEpochMs: Long = Long.MIN_VALUE,
    )

    private companion object {
        val RANKING =
            compareByDescending<SongScore> { it.score }
                .thenByDescending { it.lastPlayedAtEpochMs }
                .thenBy { it.songId.value }
    }
}
