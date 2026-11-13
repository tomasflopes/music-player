package dev.tomasflopes.musicplayer.core.search

/** Grouped results: a top card, then Artists / Albums / Songs. [hasMore] drives the "See all" entries. */
data class SearchResults(
    val query: String,
    val top: SearchHit?,
    val artists: List<ArtistHit>,
    val albums: List<AlbumHit>,
    val songs: List<SongHit>,
    val moreArtists: Boolean = false,
    val moreAlbums: Boolean = false,
    val moreSongs: Boolean = false,
) {
    companion object {
        fun empty(query: String = "") = SearchResults(query, null, emptyList(), emptyList(), emptyList())
    }
}

data class SearchConfig(
    val sectionSize: Int = 4,
)
