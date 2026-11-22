package dev.tomasflopes.musicplayer.ui.stats

import dev.tomasflopes.musicplayer.core.taste.stats.PeriodStatsSource
import dev.tomasflopes.musicplayer.core.taste.stats.StatsPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The period's stats with names and covers, each time the source computes them again. */
internal fun PeriodStatsSource.observeWrapped(
    period: StatsPeriod,
    resolver: WrappedDataResolver,
): Flow<WrappedState> = observe(period).map { WrappedState.Ready(resolver.resolve(it)) }
