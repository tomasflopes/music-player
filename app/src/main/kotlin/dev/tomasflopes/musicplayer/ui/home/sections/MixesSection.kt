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
import dev.tomasflopes.musicplayer.ui.components.SectionHeader
import dev.tomasflopes.musicplayer.ui.home.HomeSection
import dev.tomasflopes.musicplayer.ui.mixes.EditMixRoute
import dev.tomasflopes.musicplayer.ui.mixes.MixCard
import dev.tomasflopes.musicplayer.ui.mixes.MixesViewModel
import dev.tomasflopes.musicplayer.ui.mixes.NewMixCard
import dev.tomasflopes.musicplayer.ui.mixes.NewMixRoute
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import javax.inject.Inject

/** "Your mixes" (design 7.6, ADR 0076): a row of the owner's mixes after a "New mix" card, above the dynamic lists. */
class MixesSection
    @Inject
    constructor() : HomeSection {
        override val id = "mixes"
        override val order = 8

        @Composable
        override fun Content(onNavigate: (String) -> Unit) {
            val viewModel: MixesViewModel = hiltViewModel()
            val items by viewModel.items.collectAsStateWithLifecycle()
            Column {
                SectionHeader("Your mixes")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    item(key = "new") { NewMixCard(onClick = { onNavigate(NewMixRoute.PATTERN) }) }
                    items(items, key = { it.mix.id.value }) { item ->
                        MixCard(
                            item = item,
                            onPlay = { viewModel.start(item.mix.id) },
                            onEdit = { onNavigate(EditMixRoute.create(item.mix.id)) },
                        )
                    }
                }
            }
        }
    }
