package dev.tomasflopes.musicplayer.ui.search

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType

/** One result, as the screen shows it and as the log records it. */
sealed interface SearchItem {
    val type: SearchItemType
    val id: Long

    /** Stable list key, unique across types. */
    val key: String get() = "${type.name}-$id"
}

data class ArtistItem(
    val artist: Artist,
) : SearchItem {
    override val type: SearchItemType get() = SearchItemType.ARTIST
    override val id: Long get() = artist.id.value
}

/** [cover] is a song of the album whose embedded art stands in for it, or `null` when there is none. */
data class AlbumItem(
    val album: Album,
    val cover: Song?,
) : SearchItem {
    override val type: SearchItemType get() = SearchItemType.ALBUM
    override val id: Long get() = album.id.value
}

data class SongItem(
    val song: Song,
) : SearchItem {
    override val type: SearchItemType get() = SearchItemType.SONG
    override val id: Long get() = song.id.value
}

/** The main results: a top card, then at most a few of each kind. `more*` says "See all" has something to add. */
data class SearchSections(
    val top: SearchItem,
    val artists: List<ArtistItem>,
    val albums: List<AlbumItem>,
    val songs: List<SongItem>,
    val moreArtists: Boolean,
    val moreAlbums: Boolean,
    val moreSongs: Boolean,
)

sealed interface SearchUiState {
    /** The query is blank. */
    data object Idle : SearchUiState

    /** There is a query but the index of the current library is still being built. */
    data object Indexing : SearchUiState

    data class NoResults(
        val query: String,
    ) : SearchUiState

    data class Results(
        val sections: SearchSections,
    ) : SearchUiState

    /** Every match of one kind, mid-word matches last. */
    data class SeeAll(
        val type: SearchItemType,
        val items: List<SearchItem>,
    ) : SearchUiState
}
