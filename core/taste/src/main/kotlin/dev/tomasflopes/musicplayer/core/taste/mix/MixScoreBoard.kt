package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.model.ports.PlayEventSource
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.score.BrowsingBursts
import dev.tomasflopes.musicplayer.core.taste.score.PlayOutcome
import dev.tomasflopes.musicplayer.core.taste.score.SignalRules
import dev.tomasflopes.musicplayer.core.taste.score.TasteScorer
import kotlinx.coroutines.flow.first

private const val MS_PER_DAY = 86_400_000L

/** What a mix has learned so far, read once per batch (ADR 0085). One-shot: nothing observes the log here. */
fun interface MixScoreBoard {
    suspend fun of(
        mixId: MixId,
        now: Long,
    ): MixLearning
}

/**
 * Scores a mix's own rows on demand (ADR 0085, like ADR 0023 for the global score): one read of the log since the decay
 * horizon, the rows of [mixId] kept in memory, and a [TasteScorer] with a skip share of 1.0, so inside its own mix a
 * skip counts in full (the share of ADR 0084 is for the global score). Nothing is stored. If this measures slow on the
 * full log, the next step is a `mix_id` index or a port method that filters in the query, not a score table.
 */
class OnDemandMixScoreBoard(
    private val events: PlayEventSource,
    taste: TasteConfig,
    private val config: MixConfig,
) : MixScoreBoard {
    private val horizonMs = taste.decay.horizonMs
    private val scorer = TasteScorer(taste.copy(skipShare = 1.0))
    private val rules = SignalRules(taste.weights)
    private val bursts = BrowsingBursts(taste.burst)

    override suspend fun of(
        mixId: MixId,
        now: Long,
    ): MixLearning {
        val since = now - horizonMs
        val rows = events.observeSince(since).first().filter { it.mixId == mixId.value && it.startedAtEpochMs >= since }
        if (rows.isEmpty()) return MixLearning.EMPTY
        val scores = scorer.score(rows, now)
        val coolDownFrom = now - config.mixRepeatDays * MS_PER_DAY
        val outcomes = rows.map(rules::outcomeOf)
        val softened = bursts.softened(rows, outcomes)
        return MixLearning(
            liked = scores.filter { it.score >= config.likedMinScore }.map { it.songId },
            banned = scores.filter { it.score <= config.bannedScore }.mapTo(HashSet()) { it.songId },
            playedInMix = rows.filter { it.startedAtEpochMs >= coolDownFrom }.mapTo(HashSet()) { it.songId },
            informativeRows = rows.indices.count { outcomes[it] != PlayOutcome.NEUTRAL && !softened[it] },
        )
    }
}
