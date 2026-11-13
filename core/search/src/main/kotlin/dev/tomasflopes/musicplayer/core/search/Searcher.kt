package dev.tomasflopes.musicplayer.core.search

import dev.tomasflopes.musicplayer.core.model.log.SearchItemType

/**
 * Groups the index's matches into the screen's shape: one top result, then Artists / Albums / Songs.
 *
 * Main results only contain exact and whole-word / starts-with matches. Mid-word matches are deliberately
 * kept for "See all", so searching `ed` does not bury the artist Ed under every song containing "ed".
 */
class Searcher(
    private val index: SearchIndex,
    private val config: SearchConfig = SearchConfig(),
) {
    fun search(query: String): SearchResults {
        val matches = index.search(query)
        if (matches.isEmpty()) return SearchResults.empty(query)

        val main = matches.filter { it.tier != MatchTier.CONTAINS }.sortedWith(hitOrder)
        val artistMatches = main.filter { it.doc.type == SearchItemType.ARTIST }
        val albumMatches = main.filter { it.doc.type == SearchItemType.ALBUM }
        val songMatches = main.filter { it.doc.type == SearchItemType.SONG }
        val totalByType = matches.groupingBy { it.doc.type }.eachCount()

        return SearchResults(
            query = query,
            top = main.sortedWith(topOrder).firstOrNull()?.toHit(),
            artists = artistMatches.take(config.sectionSize).mapNotNull { it.toHit() as? ArtistHit },
            albums = albumMatches.take(config.sectionSize).mapNotNull { it.toHit() as? AlbumHit },
            songs = songMatches.take(config.sectionSize).mapNotNull { it.toHit() as? SongHit },
            moreArtists = totalByType.hasMoreThan(SearchItemType.ARTIST, artistMatches.size, config.sectionSize),
            moreAlbums = totalByType.hasMoreThan(SearchItemType.ALBUM, albumMatches.size, config.sectionSize),
            moreSongs = totalByType.hasMoreThan(SearchItemType.SONG, songMatches.size, config.sectionSize),
        )
    }

    /** "See all": every match of [type], mid-word substring matches last. */
    fun searchAll(
        query: String,
        type: SearchItemType,
    ): List<SearchHit> =
        index
            .search(query)
            .filter { it.doc.type == type }
            .sortedWith(hitOrder)
            .mapNotNull { it.toHit() }

    // "See all" must be offered whenever it would show something the section does not, and that includes the
    // mid-word matches the section never lists, even when the section itself is not full.
    private fun Map<SearchItemType, Int>.hasMoreThan(
        type: SearchItemType,
        mainCount: Int,
        sectionSize: Int,
    ): Boolean = (this[type] ?: 0) > minOf(mainCount, sectionSize)

    private fun DocMatch.toHit(): SearchHit? =
        when (doc.type) {
            SearchItemType.ARTIST -> index.artist(doc.id)?.let { ArtistHit(it, tier, field) }
            SearchItemType.ALBUM -> index.album(doc.id)?.let { AlbumHit(it, tier, field) }
            SearchItemType.SONG -> index.song(doc.id)?.let { SongHit(it, tier, field) }
        }

    private companion object {
        const val ARTIST_RANK = 3
        const val ALBUM_RANK = 2
        const val SONG_RANK = 1

        val hitOrder =
            compareBy<DocMatch>(
                { -it.tier.rank },
                { it.field.ordinal },
                { it.doc.sortKey },
            )

        val topOrder =
            compareBy<DocMatch>(
                { -it.tier.rank },
                { -typeRank(it.doc.type) },
                { it.field.ordinal },
                { it.doc.sortKey },
            )

        fun typeRank(type: SearchItemType): Int =
            when (type) {
                SearchItemType.ARTIST -> ARTIST_RANK
                SearchItemType.ALBUM -> ALBUM_RANK
                SearchItemType.SONG -> SONG_RANK
            }
    }
}
