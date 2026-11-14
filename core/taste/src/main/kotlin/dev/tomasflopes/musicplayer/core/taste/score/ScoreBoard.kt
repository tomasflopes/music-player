package dev.tomasflopes.musicplayer.core.taste.score

import kotlinx.coroutines.flow.Flow

/**
 * Where the global taste scores come from. This is the decision point of ADR 0023: today [OnDemandScoreBoard]
 * scores the listening log whenever it is read; a cached implementation can replace it by changing one binding,
 * and the lists built on top (Top 100 now; Discover, Rewind and smart shuffle later) do not change.
 */
interface ScoreBoard {
    /** Every song that has a score, best first. Emits again when the listening log changes. */
    fun observeRanked(): Flow<List<SongScore>>
}
