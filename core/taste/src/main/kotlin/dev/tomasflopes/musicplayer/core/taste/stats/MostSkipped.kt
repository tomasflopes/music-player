package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.taste.config.StatsConfig
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.score.BrowsingBursts
import dev.tomasflopes.musicplayer.core.taste.score.PlayOutcome
import dev.tomasflopes.musicplayer.core.taste.score.SignalRules

data class SongSkips(
    val songId: SongId,
    val skips: Int,
)

/**
 * The songs the owner skipped most (design §7.10): real skips only, every one with the same weight whatever the
 * source (the ×0.3 share of ADR 0084 belongs to taste scores). A row is a real skip when it ended as skipped and
 * [SignalRules] does not call it finished; a row inside a browsing burst ([BrowsingBursts]) is looking around, not
 * rejecting, so it is left out. Both rules are the taste code's own.
 */
class MostSkipped(
    tasteConfig: TasteConfig,
    private val config: StatsConfig,
) {
    private val rules = SignalRules(tasteConfig.weights)
    private val bursts = BrowsingBursts(tasteConfig.burst)

    /** [rows] of one period, oldest first. */
    fun of(rows: List<PlayEvent>): List<SongSkips> {
        val outcomes = rows.map(rules::outcomeOf)
        val inBurst = bursts.softened(rows, outcomes)
        return rows.indices
            .filter { rows[it].endedBy == EndedBy.SKIPPED && outcomes[it] in SKIPS && !inBurst[it] }
            .groupingBy { rows[it].songId.value }
            .eachCount()
            .filterValues { it >= config.minSkips }
            .entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .take(config.topSize)
            .map { SongSkips(SongId(it.key), it.value) }
    }

    private companion object {
        val SKIPS = setOf(PlayOutcome.SKIPPED_EARLY, PlayOutcome.SKIPPED_MID, PlayOutcome.SKIPPED_LATE)
    }
}
