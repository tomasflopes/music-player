package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryReader
import dev.tomasflopes.musicplayer.core.model.stats.ListeningAggregates
import dev.tomasflopes.musicplayer.core.taste.config.StatsConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * The stats of a period, computed on demand off the main thread (ADR 0088, in the spirit of ADR 0023). A closed
 * period can never change, so it is computed once and kept in memory for the life of the process. The current
 * period, and one that ended less than [StatsConfig.closedAfterMs] ago, is computed again whenever the log changes.
 * Nothing is stored. If one period measures slow on the phone, the next step is a stats cache table.
 */
class PeriodStatsSource(
    private val computer: StatsComputer,
    private val history: PlayHistoryReader,
    private val aggregates: ListeningAggregates,
    private val calendar: PeriodCalendar,
    private val clock: Clock,
    private val config: StatsConfig,
    private val dispatcher: CoroutineDispatcher,
) {
    private val closed = ConcurrentHashMap<StatsPeriod, PeriodStats>()

    fun currentMonth(): StatsPeriod = calendar.containing(PeriodKind.MONTH, clock.millis())

    suspend fun pastMonths(): List<StatsPeriod> = calendar.pastMonths(aggregates.earliestPlayAt(), clock.millis())

    suspend fun years(): List<StatsPeriod> = calendar.years(aggregates.earliestPlayAt(), clock.millis())

    /** The past months again whenever the log changes, so a first play shows up without a restart. */
    fun observePastMonths(): Flow<List<StatsPeriod>> =
        history
            .observeCount()
            .distinctUntilChanged()
            .map { pastMonths() }
            .flowOn(dispatcher)

    fun observeYears(): Flow<List<StatsPeriod>> =
        history
            .observeCount()
            .distinctUntilChanged()
            .map { years() }
            .flowOn(dispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(period: StatsPeriod): Flow<PeriodStats> =
        if (calendar.isClosed(period, clock.millis(), config.closedAfterMs)) {
            flow { emit(closed[period] ?: computer.compute(period).also { closed[period] = it }) }.flowOn(dispatcher)
        } else {
            history
                .observeCount()
                .distinctUntilChanged()
                .mapLatest { computer.compute(period) }
                .flowOn(dispatcher)
        }
}
