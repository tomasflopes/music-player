package dev.tomasflopes.musicplayer.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.ui.components.ScreenTitle
import dev.tomasflopes.musicplayer.ui.library.albums.AlbumsTab
import dev.tomasflopes.musicplayer.ui.library.artists.ArtistsTab
import dev.tomasflopes.musicplayer.ui.library.songs.SongsTab

/** Where Library rows lead: the artist and album pages (plan Task 26). */
private class LibraryActions(
    val onOpenArtist: (ArtistId) -> Unit,
    val onOpenAlbum: (AlbumId) -> Unit,
)

/** One sub-tab of Library. State inside the Library route, not a destination of its own (ADR 0016). */
private class LibraryPage(
    val title: String,
    val content: @Composable (LibraryActions) -> Unit,
)

/** In display order. Folders (plan Task 27) is one more entry, between Albums and Songs. */
private val pages =
    listOf(
        LibraryPage("Artists") { ArtistsTab(it.onOpenArtist) },
        LibraryPage("Albums") { AlbumsTab(it.onOpenAlbum) },
        LibraryPage("Songs") { SongsTab() },
    )

@Composable
fun LibraryScreen(
    onOpenArtist: (ArtistId) -> Unit,
    onOpenAlbum: (AlbumId) -> Unit,
) {
    val actions = remember(onOpenArtist, onOpenAlbum) { LibraryActions(onOpenArtist, onOpenAlbum) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    // Each sub-tab keeps its scroll position while another one is showing.
    val stateHolder = rememberSaveableStateHolder()

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Library")
        PrimaryTabRow(selectedTabIndex = selected) {
            pages.forEachIndexed { index, page ->
                Tab(selected = index == selected, onClick = { selected = index }, text = { Text(page.title) })
            }
        }
        stateHolder.SaveableStateProvider(selected) { pages[selected].content(actions) }
    }
}
