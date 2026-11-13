package dev.tomasflopes.musicplayer.ui.library.artist

import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.ui.library.albums.AlbumListItem

/** The groups of an artist page, in the order they are shown. */
enum class ArtistSection(
    val title: String,
) {
    ALBUMS("Albums"),
    SONGS("Songs"),
    FEATURED_ON("Featured on"),
}

/** One line of the page. A group with nothing in it has no header and no lines. */
sealed interface ArtistEntry {
    /** Stable list key, unique within the page. */
    val key: String

    data class Header(
        val section: ArtistSection,
    ) : ArtistEntry {
        override val key: String get() = "header-${section.name}"
    }

    data class Album(
        val item: AlbumListItem,
    ) : ArtistEntry {
        override val key: String get() = "album-${item.album.id.value}"
    }

    /** One of the artist's own songs; [index] is its place in [ArtistUiState.Ready.songs]. */
    data class Track(
        val index: Int,
        val song: Song,
    ) : ArtistEntry {
        override val key: String get() = "track-${song.id.value}"
    }

    /** A song by someone else that credits the artist in its title; [index] is its place in `featuredOn`. */
    data class Featured(
        val index: Int,
        val song: Song,
    ) : ArtistEntry {
        override val key: String get() = "featured-${song.id.value}"
    }
}

sealed interface ArtistUiState {
    /** No artist opened yet. */
    data object Loading : ArtistUiState

    /** The route named an artist that is not in the library (any more). */
    data object NotFound : ArtistUiState

    /** [songs] are the artist's own songs and [featuredOn] the songs they feature on, each in its shown order. */
    data class Ready(
        val artist: Artist,
        val songs: List<Song>,
        val featuredOn: List<Song>,
        val entries: List<ArtistEntry>,
    ) : ArtistUiState
}
