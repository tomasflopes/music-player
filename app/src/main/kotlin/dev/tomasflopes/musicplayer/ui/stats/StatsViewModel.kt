package dev.tomasflopes.musicplayer.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.taste.stats.PeriodStatsSource
import dev.tomasflopes.musicplayer.core.taste.stats.StatsPeriod
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The three views of the Stats tab (design §8.2). */
enum class StatsView(
    val label: String,
) {
    THIS_MONTH("This month"),
    PAST_MONTHS("Past months"),
    YEARS("Years"),
}

/** The Stats tab: the current month's Wrapped, and the lists of past months and years that open a Wrapped page. */
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        source: PeriodStatsSource,
        resolver: WrappedDataResolver,
        cards: Set<@JvmSuppressWildcards WrappedCard>,
    ) : ViewModel() {
        val cards: List<WrappedCard> = cards.sortedWith(compareBy({ it.order }, { it.id }))

        val currentMonth: StatsPeriod = source.currentMonth()

        private val selected = MutableStateFlow(StatsView.THIS_MONTH)
        val view: StateFlow<StatsView> = selected.asStateFlow()

        val thisMonth: StateFlow<WrappedState> =
            source.observeWrapped(currentMonth, resolver).stateIn(viewModelScope, WhileSubscribed, WrappedState.Loading)

        /** `null` until the first read, so "no past months" and "still loading" look different. */
        val pastMonths: StateFlow<List<StatsPeriod>?> =
            source.observePastMonths().stateIn(viewModelScope, WhileSubscribed, null)

        val years: StateFlow<List<StatsPeriod>?> = source.observeYears().stateIn(viewModelScope, WhileSubscribed, null)

        fun select(view: StatsView) {
            selected.value = view
        }
    }
