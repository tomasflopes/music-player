package dev.tomasflopes.musicplayer.ui.home.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.components.ListCard
import dev.tomasflopes.musicplayer.ui.components.NewPlaylistCard
import dev.tomasflopes.musicplayer.ui.components.SectionHeader
import dev.tomasflopes.musicplayer.ui.home.HomeSection
import dev.tomasflopes.musicplayer.ui.playlists.PlaylistNameDialog
import dev.tomasflopes.musicplayer.ui.playlists.PlaylistRoute
import dev.tomasflopes.musicplayer.ui.playlists.PlaylistsViewModel
import dev.tomasflopes.musicplayer.ui.playlists.openNewPlaylist
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The owner's playlists as cards, most recently used first, after a "New playlist" card (design 8.2). */
class PlaylistsSection
    @Inject
    constructor() : HomeSection {
        override val id = "playlists"
        override val order = 30

        @Composable
        override fun Content(onNavigate: (String) -> Unit) {
            val viewModel: PlaylistsViewModel = hiltViewModel()
            val items by viewModel.items.collectAsStateWithLifecycle()
            var naming by rememberSaveable { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            Column {
                SectionHeader("Playlists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    item(key = "new") { NewPlaylistCard(onClick = { naming = true }) }
                    items(items, key = { it.playlist.id.value }) { item ->
                        ListCard(
                            title = item.playlist.name,
                            subtitle = songCountLabel(item.playlist.songCount),
                            cover = item.cover,
                            onClick = { onNavigate(PlaylistRoute.create(item.playlist.id)) },
                        )
                    }
                }
            }
            if (naming) {
                PlaylistNameDialog(
                    title = "New playlist",
                    confirmLabel = "Create",
                    onConfirm = { name ->
                        naming = false
                        scope.launch { viewModel.create(name)?.let(onNavigate::openNewPlaylist) }
                    },
                    onDismiss = { naming = false },
                )
            }
        }
    }
