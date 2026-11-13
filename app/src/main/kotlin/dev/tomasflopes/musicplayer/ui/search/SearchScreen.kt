package dev.tomasflopes.musicplayer.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/** What the screen can ask for, so the composables below take one parameter instead of seven callbacks. */
private class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onSeeAll: (SearchItemType) -> Unit,
    val onCloseSeeAll: () -> Unit,
    val onPlayTop: (SearchItem) -> Unit,
    val onShuffleTop: (SearchItem) -> Unit,
    val onResult: (SearchItem) -> Unit,
    val onSong: (Int) -> Unit,
)

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel) {
            SearchActions(
                onQueryChange = viewModel::onQueryChange,
                onSeeAll = viewModel::onSeeAll,
                onCloseSeeAll = viewModel::onCloseSeeAll,
                onPlayTop = viewModel::onPlayTop,
                onShuffleTop = viewModel::onShuffleTop,
                onResult = viewModel::onResultClick,
                onSong = viewModel::onSongClick,
            )
        }
    BackHandler(enabled = state is SearchUiState.SeeAll, onBack = actions.onCloseSeeAll)
    SearchContent(query, state, actions)
}

@Composable
private fun SearchContent(
    query: String,
    state: SearchUiState,
    actions: SearchActions,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Search")
        SearchField(query, actions.onQueryChange)
        when (state) {
            SearchUiState.Idle -> EmptyState("Search your songs, artists and albums.")
            SearchUiState.Indexing -> EmptyState("Getting your library ready...")
            is SearchUiState.NoResults -> EmptyState("No matches for \"${state.query.trim()}\".")
            is SearchUiState.Results -> ResultsList(state.sections, actions)
            is SearchUiState.SeeAll -> SeeAllList(state, actions)
        }
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
            ArtistRow(item.artist, onClick = { actions.onResult(item) })
        }
        section("Albums", sections.albums, sections.moreAlbums, SearchItemType.ALBUM, actions) { item ->
            AlbumRow(item.album, item.cover, onClick = { actions.onResult(item) })
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
                leading = { ArtistAvatar(ArtSize.card) },
                onPlay = { actions.onPlayTop(item) },
                onShuffle = { actions.onShuffleTop(item) },
            )
        is AlbumItem ->
            TopResultCard(
                title = item.album.title,
                subtitle = "Album · ${item.album.albumArtist}",
                leading = { AlbumArt(item.cover, ArtSize.card) },
                onPlay = { actions.onPlayTop(item) },
                onShuffle = { actions.onShuffleTop(item) },
            )
        is SongItem ->
            TopResultCard(
                title = item.song.title,
                subtitle = "Song · ${item.song.artistName}",
                leading = { AlbumArt(item.song, ArtSize.card) },
                onPlay = { actions.onPlayTop(item) },
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
                    is ArtistItem -> ArtistRow(item.artist, onClick = { actions.onResult(item) })
                    is AlbumItem -> AlbumRow(item.album, item.cover, onClick = { actions.onResult(item) })
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
