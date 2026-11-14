package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.taste.config.BurstRule

/**
 * Finds browsing bursts (design §7.4): [BurstRule.minSkips] or more consecutive log rows that were each skipped
 * inside the first seconds, with the starts close together. Skips like that mean "looking for something", so the
 * scorer softens them. Any other row, or a long pause, ends a run.
 */
class BrowsingBursts(
    private val rule: BurstRule = BurstRule(),
) {
    /** True at every index of [events] that sits inside a burst. [outcomes] has one entry per event. */
    fun softened(
        events: List<PlayEvent>,
        outcomes: List<PlayOutcome>,
    ): BooleanArray {
        val result = BooleanArray(events.size)
        quickSkipRuns(events, outcomes).flatten().forEach { result[it] = true }
        return result
    }

    private fun quickSkipRuns(
        events: List<PlayEvent>,
        outcomes: List<PlayOutcome>,
    ): List<List<Int>> {
        val runs = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()

        fun close() {
            if (current.size >= rule.minSkips) runs += current
            current = mutableListOf()
        }

        events.indices.forEach { index ->
            val quick = outcomes[index] == PlayOutcome.SKIPPED_EARLY
            val previous = current.lastOrNull()
            val tooFarApart =
                previous != null && events[index].startedAtEpochMs - events[previous].startedAtEpochMs > rule.maxGapMs
            if (!quick || tooFarApart) close()
            if (quick) current += index
        }
        close()
        return runs
    }
}
