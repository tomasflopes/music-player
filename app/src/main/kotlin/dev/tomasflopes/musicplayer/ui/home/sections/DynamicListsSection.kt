package dev.tomasflopes.musicplayer.ui.home.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.components.ListCard
import dev.tomasflopes.musicplayer.ui.components.SectionHeader
import dev.tomasflopes.musicplayer.ui.home.HomeSection
import dev.tomasflopes.musicplayer.ui.home.dynamiclist.DynamicListRoute
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import javax.inject.Inject

/** A row of cards, one per dynamic list (Recently added now; Top 100, Discover and Rewind later). */
class DynamicListsSection
    @Inject
    constructor() : HomeSection {
        override val id = "dynamic_lists"
        override val order = 10

        @Composable
        override fun Content(onNavigate: (String) -> Unit) {
            val viewModel: DynamicListsViewModel = hiltViewModel()
            val cards by viewModel.cards.collectAsStateWithLifecycle()
            if (cards.isEmpty()) return
            // One column: several roots in a lazy item would be drawn on top of each other.
            Column {
                SectionHeader("Lists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(cards, key = { it.id }) { card ->
                        ListCard(
                            title = card.title,
                            subtitle = songCountLabel(card.songCount),
                            cover = card.cover,
                            onClick = { onNavigate(DynamicListRoute.create(card.id)) },
                        )
                    }
                }
            }
        }
    }

internal fun songCountLabel(count: Int): String = if (count == 1) "1 song" else "$count songs"
