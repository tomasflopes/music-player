package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.ports.PlayEventSource
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import kotlin.coroutines.CoroutineContext

/**
 * Scores the last `horizonDays` of the listening log each time it is read or changes (ADR 0023). Nothing is
 * stored, so a changed weight applies at once and there is nothing to rebuild. The scoring runs on [computeOn]
 * because a year of log is tens of thousands of rows and the collector is usually the main thread.
 */
class OnDemandScoreBoard(
    private val events: PlayEventSource,
    private val scorer: TasteScorer,
    private val config: TasteConfig,
    private val clock: Clock,
    private val computeOn: CoroutineContext = Dispatchers.Default,
) : ScoreBoard {
    override fun observeRanked(): Flow<List<SongScore>> =
        flow { emitAll(events.observeSince(clock.millis() - config.decay.horizonMs)) }
            .map { rows -> scorer.score(rows, clock.millis()) }
            .flowOn(computeOn)
            .distinctUntilChanged()
}
