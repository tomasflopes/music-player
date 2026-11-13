package dev.tomasflopes.musicplayer.core.search

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.Song

const val MAX_SECTION = 5

/**
 * Turns a query into grouped results over a [SearchIndex].
 *
 * Every query term must match the song somewhere (AND across terms); a song's tier is its best single
 * (term, field) match. Phase 1 breaks ties alphabetically; phase 2 adds the recent global score.
 * Matching is over the normalized title, artist, album, album artist, folder name and featured artists.
 */
class Searcher(
    private val index: SearchIndex,
    private val knownAlbums: List<Album>,
    private val knownArtistsDomain: List<Artist>,
) {
    /** Grouped results for [query]; songs come from [songsById] (the live domain models). */
    fun search(
        query: String,
        songsById: Map<Long, Song>,
    ): SearchResult {
        val terms = Normalizer.words(query)
        if (terms.isEmpty()) return empty()

        // Candidates: songs matching every term somewhere.
        var candidates = index.songsMatching(terms[0]).toMutableSet()
        for (term in terms.drop(1)) candidates.retainAll(index.songsMatching(term))

        val scored =
            candidates.mapNotNull { id ->
                val indexSong = index.song(id) ?: return@mapNotNull null
                val domain = songsById[id] ?: return@mapNotNull null
                var best = MatchTier.CONTAINS
                var bestField = "title"
                for (term in terms) {
                    val tier = tierFor(term, indexSong)
                    if (tier.rank > best.rank) {
                        best = tier
                        bestField = fieldFor(term, indexSong)
                    }
                }
                Scored(id, domain, best, bestField)
            }

        val ordered = scored.sortedWith(compareByDescending<Scored> { it.tier.rank }.thenBy { it.title })
        val songHits = ordered.map { s -> HitSong(s.song, s.tier, s.field) }

        val top = songHits.firstOrNull()
        val songs = songHits.drop(1).take(MAX_SECTION)
        val hasMore = songHits.size > 1 + MAX_SECTION

        val artistIds = songHits.mapNotNull { it.song.artistId.value }.toSet()
        val artists = knownArtistsDomain.filter { it.id.value in artistIds }.take(MAX_SECTION)
        val albumIds = songHits.mapNotNull { it.song.albumId?.value }.toSet()
        val albums = knownAlbums.filter { it.id.value in albumIds }.take(MAX_SECTION)

        return SearchResult(query, top, artists, albums, songs, hasMore)
    }

    private fun empty() = SearchResult("", null, emptyList(), emptyList(), emptyList(), false)

    private fun tierFor(
        term: String,
        song: IndexableSong,
    ): MatchTier {
        val n = Normalizer.normalize(term)
        val fields = listOf(song.title, song.artist, song.album, song.albumArtist, song.folderName)
        return when {
            fields.any { it?.let { Normalizer.normalize(it) == n } == true } -> MatchTier.EXACT
            fields.any { it?.let { Normalizer.normalize(it).startsWith(n) } == true } ||
                song.featured.any { Normalizer.normalize(it).startsWith(n) } -> MatchTier.STARTS_WITH
            else -> MatchTier.CONTAINS
        }
    }

    private fun fieldFor(
        term: String,
        song: IndexableSong,
    ): String {
        val n = Normalizer.normalize(term)
        val fields: List<Pair<String, String?>> =
            listOf(
                "title" to song.title,
                "artist" to song.artist,
                "album" to song.album,
                "albumArtist" to song.albumArtist,
                "folder" to song.folderName,
            )
        return fields
            .firstOrNull { (_, v) ->
                v?.let { Normalizer.normalize(it).startsWith(n) } == true ||
                    v?.let { Normalizer.normalize(it).contains(n) } == true
            }?.first
            ?: song.featured.firstOrNull { Normalizer.normalize(it).contains(n) }?.let { "featured" }
            ?: "title"
    }

    private data class Scored(
        val id: Long,
        val song: Song,
        val tier: MatchTier,
        val field: String,
    ) {
        val title: String get() = song.title
    }
}
