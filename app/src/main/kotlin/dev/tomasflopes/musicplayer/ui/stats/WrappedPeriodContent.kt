package dev.tomasflopes.musicplayer.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.core.taste.stats.StatsPeriod
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** The cards of one period, in order, each only when it has something to show; a clean empty state otherwise. */
@Composable
fun WrappedPeriodContent(
    state: WrappedState,
    period: StatsPeriod,
    cards: List<WrappedCard>,
    modifier: Modifier = Modifier,
) {
    when (state) {
        WrappedState.Loading ->
            Box(
                modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        is WrappedState.Ready -> {
            val visible = cards.filter { it.hasContent(state.data) }
            if (state.data.isEmpty || visible.isEmpty()) {
                EmptyState("Nothing played in ${periodLabel(period)} yet.", modifier)
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    items(visible, key = { it.id }) { card -> card.Content(state.data, Modifier) }
                }
            }
        }
    }
}
