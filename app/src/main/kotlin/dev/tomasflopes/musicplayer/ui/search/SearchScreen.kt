package dev.tomasflopes.musicplayer.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType
import dev.tomasflopes.musicplayer.ui.components.AlbumArt
import dev.tomasflopes.musicplayer.ui.components.AlbumRow
import dev.tomasflopes.musicplayer.ui.components.ArtistAvatar
import dev.tomasflopes.musicplayer.ui.components.ArtistRow
import dev.tomasflopes.musicplayer.ui.components.EmptyState
import dev.tomasflopes.musicplayer.ui.components.ScreenTitle
import dev.tomasflopes.musicplayer.ui.components.SearchField
import dev.tomasflopes.musicplayer.ui.components.SectionHeader
import dev.tomasflopes.musicplayer.ui.components.SongRow
import dev.tomasflopes.musicplayer.ui.components.TopResultCard
import dev.tomasflopes.musicplayer.ui.theme.ArtSize
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** What the screen can ask for, so the composables below take one parameter instead of a callback per button. */
private class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onSeeAll: (SearchItemType) -> Unit,
    val onCloseSeeAll: () -> Unit,
    val onPlayTop: (SearchItem) -> Unit,
    val onShuffleTop: (SearchItem) -> Unit,
    val onOpen: (SearchItem) -> Unit,
    val onSong: (Int) -> Unit,
)

/** [onOpenArtist] and [onOpenAlbum] navigate to the page of the chosen result; the choice is logged here first. */
@Composable
fun SearchScreen(
    onOpenArtist: (ArtistId) -> Unit,
    onOpenAlbum: (AlbumId) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel, onOpenArtist, onOpenAlbum) {
            SearchActions(
                onQueryChange = viewModel::onQueryChange,
                onSeeAll = viewModel::onSeeAll,
                onCloseSeeAll = viewModel::onCloseSeeAll,
                onPlayTop = viewModel::onPlayTop,
                onShuffleTop = viewModel::onShuffleTop,
                onOpen = { item ->
                    viewModel.onOpenResult(item)
                    when (item) {
                        is ArtistItem -> onOpenArtist(item.artist.id)
                        is AlbumItem -> onOpenAlbum(item.album.id)
                        is SongItem -> Unit
                    }
                },
                onSong = viewModel::onSongClick,
            )
        }
    BackHandler(enabled = state is SearchUiState.SeeAll, onBack = actions.onCloseSeeAll)
    SearchContent(query, state, searching, actions)
}

@Composable
private fun SearchContent(
    query: String,
    state: SearchUiState,
    searching: Boolean,
    actions: SearchActions,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Search")
        SearchField(query, actions.onQueryChange, Modifier.focusRequester(focus))
        SearchProgress(visible = searching || state == SearchUiState.Indexing)
        when (state) {
            SearchUiState.Idle -> EmptyState("Search your songs, artists and albums.")
            SearchUiState.Indexing -> EmptyState("Getting your library ready...")
            is SearchUiState.NoResults -> EmptyState("No matches for \"${state.query.trim()}\".")
            is SearchUiState.Results -> ResultsList(state.sections, actions)
            is SearchUiState.SeeAll -> SeeAllList(state, actions)
        }
    }
}

/** A thin bar under the field while results are on their way; its height is always reserved so nothing jumps. */
@Composable
private fun SearchProgress(visible: Boolean) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xxxs).height(4.dp)) {
        if (visible) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResultsList(
    sections: SearchSections,
    actions: SearchActions,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "top") { TopResult(sections.top, actions) }
        section("Artists", sections.artists, sections.moreArtists, SearchItemType.ARTIST, actions) { item ->
            ArtistRow(item.artist, onClick = { actions.onOpen(item) })
        }
        section("Albums", sections.albums, sections.moreAlbums, SearchItemType.ALBUM, actions) { item ->
            AlbumRow(item.album, item.cover, onClick = { actions.onOpen(item) })
        }
        if (sections.songs.isNotEmpty()) {
            item(key = "songs-header") {
                SectionHeader("Songs", onSeeAll = seeAll(sections.moreSongs, SearchItemType.SONG, actions))
            }
            itemsIndexed(sections.songs, key = { _, item -> item.key }) { index, item ->
                SongRow(item.song, onClick = { actions.onSong(index) })
            }
        }
    }
}

private fun <T : SearchItem> LazyListScope.section(
    title: String,
    entries: List<T>,
    more: Boolean,
    type: SearchItemType,
    actions: SearchActions,
    row: @Composable (T) -> Unit,
) {
    if (entries.isEmpty()) return
    item(key = "${type.name}-header") { SectionHeader(title, onSeeAll = seeAll(more, type, actions)) }
    items(entries, key = { it.key }) { row(it) }
}

private fun seeAll(
    more: Boolean,
    type: SearchItemType,
    actions: SearchActions,
): (() -> Unit)? = if (more) ({ actions.onSeeAll(type) }) else null

@Composable
private fun TopResult(
    item: SearchItem,
    actions: SearchActions,
) {
    when (item) {
        is ArtistItem ->
            TopResultCard(
                title = item.artist.name,
                subtitle = "Artist",
                leading = { ArtistAvatar(item.artist.id, item.artist.name, ArtSize.card) },
                onPlay = { actions.onPlayTop(item) },
                onShuffle = { actions.onShuffleTop(item) },
                onClick = { actions.onOpen(item) },
            )
        is AlbumItem ->
            TopResultCard(
                title = item.album.title,
                subtitle = "Album · ${item.album.albumArtist}",
                leading = { AlbumArt(item.cover, ArtSize.card) },
                onPlay = { actions.onPlayTop(item) },
                onShuffle = { actions.onShuffleTop(item) },
                onClick = { actions.onOpen(item) },
            )
        is SongItem ->
            TopResultCard(
                title = item.song.title,
                subtitle = "Song · ${item.song.artistName}",
                leading = { AlbumArt(item.song, ArtSize.card) },
                onPlay = { actions.onPlayTop(item) },
                onClick = { actions.onPlayTop(item) },
            )
    }
}

@Composable
private fun SeeAllList(
    state: SearchUiState.SeeAll,
    actions: SearchActions,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onCloseSeeAll) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to results")
            }
            Text(state.type.seeAllTitle(), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.items, key = { _, item -> item.key }) { index, item ->
                when (item) {
                    is ArtistItem -> ArtistRow(item.artist, onClick = { actions.onOpen(item) })
                    is AlbumItem -> AlbumRow(item.album, item.cover, onClick = { actions.onOpen(item) })
                    is SongItem -> SongRow(item.song, onClick = { actions.onSong(index) })
                }
            }
        }
    }
}

private fun SearchItemType.seeAllTitle(): String =
    when (this) {
        SearchItemType.ARTIST -> "Artists"
        SearchItemType.ALBUM -> "Albums"
        SearchItemType.SONG -> "Songs"
    }
