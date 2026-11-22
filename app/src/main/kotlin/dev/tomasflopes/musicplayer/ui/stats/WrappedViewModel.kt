package dev.tomasflopes.musicplayer.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.taste.stats.PeriodStatsSource
import dev.tomasflopes.musicplayer.core.taste.stats.StatsPeriod
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Wrapped page of the period named by the route; a key that names none shows the current month. */
@HiltViewModel
class WrappedViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        source: PeriodStatsSource,
        resolver: WrappedDataResolver,
        cards: Set<@JvmSuppressWildcards WrappedCard>,
    ) : ViewModel() {
        val cards: List<WrappedCard> = cards.sortedWith(compareBy({ it.order }, { it.id }))

        val period: StatsPeriod =
            savedState.get<String>(WrappedRoute.ARG_KEY)?.let(StatsPeriod::parse) ?: source.currentMonth()

        val state: StateFlow<WrappedState> =
            source.observeWrapped(period, resolver).stateIn(viewModelScope, WhileSubscribed, WrappedState.Loading)
    }
