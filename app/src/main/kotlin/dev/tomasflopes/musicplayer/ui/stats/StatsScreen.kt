package dev.tomasflopes.musicplayer.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.taste.stats.StatsPeriod
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.ScreenTitle
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** The Stats tab: this month's Wrapped, and past months and years that open their own page. */
@Composable
fun StatsScreen(
    onOpenPeriod: (StatsPeriod) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val view by viewModel.view.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Stats")
        ViewSwitcher(view, viewModel::select)
        when (view) {
            StatsView.THIS_MONTH -> {
                val state by viewModel.thisMonth.collectAsStateWithLifecycle()
                WrappedPeriodContent(state, viewModel.currentMonth, viewModel.cards)
            }
            StatsView.PAST_MONTHS -> {
                val months by viewModel.pastMonths.collectAsStateWithLifecycle()
                PeriodList(months, "No past months yet. A month shows up here once it is over.", onOpenPeriod)
            }
            StatsView.YEARS -> {
                val years by viewModel.years.collectAsStateWithLifecycle()
                PeriodList(years, "No years yet. Play something and it shows up here.", onOpenPeriod)
            }
        }
    }
}

@Composable
private fun ViewSwitcher(
    selected: StatsView,
    onSelect: (StatsView) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xxs)) {
        StatsView.entries.forEachIndexed { index, view ->
            SegmentedButton(
                selected = view == selected,
                onClick = { onSelect(view) },
                shape = SegmentedButtonDefaults.itemShape(index, StatsView.entries.size),
            ) { Text(view.label) }
        }
    }
}

/** Null while the list is loading: nothing is drawn, so "empty" is never flashed before the answer. */
@Composable
private fun PeriodList(
    periods: List<StatsPeriod>?,
    emptyText: String,
    onOpen: (StatsPeriod) -> Unit,
) {
    when {
        periods == null -> Unit
        periods.isEmpty() -> EmptyState(emptyText)
        else ->
            LazyColumn(Modifier.fillMaxSize()) {
                items(periods, key = { it.key }) { period -> PeriodRow(period, onClick = { onOpen(period) }) }
            }
    }
}

@Composable
private fun PeriodRow(
    period: StatsPeriod,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                ).padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(periodLabel(period), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
