package dev.tomasflopes.musicplayer.ui.library.playlists

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.components.NewPlaylistRow
import dev.tomasflopes.musicplayer.ui.components.PlaylistRow
import dev.tomasflopes.musicplayer.ui.playlists.PlaylistNameDialog
import dev.tomasflopes.musicplayer.ui.playlists.PlaylistRoute
import dev.tomasflopes.musicplayer.ui.playlists.PlaylistsViewModel
import dev.tomasflopes.musicplayer.ui.playlists.openNewPlaylist
import kotlinx.coroutines.launch

/** The Playlists sub-tab of Library: "New playlist", then every playlist, most recently used first. */
@Composable
fun PlaylistsTab(
    onNavigate: (String) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var naming by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LazyColumn {
        item(key = "new") { NewPlaylistRow(onClick = { naming = true }) }
        items(items, key = { it.playlist.id.value }) { item ->
            PlaylistRow(item, onClick = { onNavigate(PlaylistRoute.create(item.playlist.id)) })
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
