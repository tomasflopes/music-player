package dev.tomasflopes.musicplayer.core.search

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType

/**
 * An in-memory snapshot of the library, indexed by folded words so a query only ranks plausible documents.
 *
 * Matching itself is delegated to one [SearchMatcher] per field, so adding a field or a smarter rule never
 * touches this class. Rebuild whenever the library changes.
 */
class SearchIndex(
    matchers: Set<SearchMatcher> = FieldMatcher.defaults(),
) {
    private val matcherByField = matchers.associateBy { it.field }
    private val records = mutableListOf<DocRecord>()
    private val termIndex = mutableMapOf<String, MutableSet<Int>>()
    private val artists = mutableMapOf<Long, Artist>()
    private val albums = mutableMapOf<Long, Album>()
    private val songs = mutableMapOf<Long, Song>()

    fun rebuild(
        artists: List<Artist>,
        albums: List<Album>,
        songs: List<Song>,
        features: Map<SongId, List<String>> = emptyMap(),
    ) {
        records.clear()
        termIndex.clear()
        this.artists.clear()
        this.albums.clear()
        this.songs.clear()

        artists.forEach { artist ->
            this.artists[artist.id.value] = artist
            addDoc(
                SearchDoc(
                    type = SearchItemType.ARTIST,
                    id = artist.id.value,
                    display = artist.name,
                    fields = mapOf(SearchField.ARTIST to listOf(artist.name)),
                ),
            )
        }
        albums.forEach { album ->
            this.albums[album.id.value] = album
            addDoc(
                SearchDoc(
                    type = SearchItemType.ALBUM,
                    id = album.id.value,
                    display = album.title,
                    fields =
                        mapOf(
                            SearchField.ALBUM to listOf(album.title),
                            SearchField.ALBUM_ARTIST to listOf(album.albumArtist),
                        ),
                ),
            )
        }
        songs.forEach { song ->
            this.songs[song.id.value] = song
            addDoc(songDoc(song, features[song.id].orEmpty()))
        }
    }

    /** Every document whose fields match [query], best tier first. Substring matches are included. */
    fun search(query: String): List<DocMatch> {
        val queryVariants = TextNormalizer.variants(query)
        return if (queryVariants.isEmpty()) {
            emptyList()
        } else {
            candidateIndexes(queryVariants).mapNotNull { index -> matchRecord(records[index], queryVariants) }
        }
    }

    fun artist(id: Long): Artist? = artists[id]

    fun album(id: Long): Album? = albums[id]

    fun song(id: Long): Song? = songs[id]

    val size: Int get() = records.size

    private fun songDoc(
        song: Song,
        featured: List<String>,
    ): SearchDoc {
        val fields =
            buildMap {
                put(SearchField.TITLE, listOf(song.title))
                put(SearchField.ARTIST, listOf(song.artistName))
                song.albumTitle?.let { put(SearchField.ALBUM, listOf(it)) }
                song.albumArtist?.let { put(SearchField.ALBUM_ARTIST, listOf(it)) }
                song.folderPath.substringAfterLast('/').takeIf { it.isNotEmpty() }?.let {
                    put(SearchField.FOLDER, listOf(it))
                }
                if (featured.isNotEmpty()) put(SearchField.FEATURED, featured)
            }
        return SearchDoc(SearchItemType.SONG, song.id.value, song.title, fields)
    }

    private fun addDoc(doc: SearchDoc) {
        val recordIndex = records.size
        records += DocRecord(doc)
        for ((field, values) in doc.fields) {
            if (field !in matcherByField) continue
            for (value in values) {
                for (variant in TextNormalizer.variants(value)) {
                    for (word in TextNormalizer.tokens(variant)) {
                        termIndex.getOrPut(word) { mutableSetOf() }.add(recordIndex)
                    }
                }
            }
        }
    }

    private fun candidateIndexes(queryVariants: Set<String>): Set<Int> =
        queryVariants.flatMap { docsForVariant(it) }.toSet()

    private fun docsForVariant(variant: String): Set<Int> {
        val terms = TextNormalizer.tokens(variant)
        if (terms.isEmpty()) return emptySet()
        return terms.map { docsForTerm(it) }.reduceOrNull { acc, docs -> acc intersect docs }.orEmpty()
    }

    private fun docsForTerm(term: String): Set<Int> =
        termIndex.entries
            .asSequence()
            .filter { (word, _) -> word == term || word.startsWith(term) || word.contains(term) }
            .flatMap { it.value.asSequence() }
            .toSet()

    private fun matchRecord(
        record: DocRecord,
        queryVariants: Set<String>,
    ): DocMatch? {
        val match = phraseMatch(record, queryVariants) ?: termMatch(record, queryVariants)
        return match?.let { DocMatch(record.doc, it.tier, it.field) }
    }

    private fun phraseMatch(
        record: DocRecord,
        queryVariants: Set<String>,
    ): FieldMatch? = bestMatch(queryVariants, record)

    // A full phrase did not match: accept the document when every word of one query variant matches
    // somewhere, in any order (e.g. "lamar kendrick"). The tier is the weakest single-word tier, so a
    // one-letter term that happens to start a word cannot promote an otherwise mid-word match.
    private fun termMatch(
        record: DocRecord,
        queryVariants: Set<String>,
    ): FieldMatch? =
        queryVariants
            .mapNotNull { termMatchForVariant(record, it) }
            .maxByOrNull { it.tier.rank }

    private fun termMatchForVariant(
        record: DocRecord,
        variant: String,
    ): FieldMatch? {
        val terms = TextNormalizer.tokens(variant)
        val matches = terms.map { bestMatch(setOf(it), record) }
        return if (matches.any { it == null }) {
            null
        } else {
            matches.filterNotNull().minByOrNull { it.tier.rank }
        }
    }

    private fun bestMatch(
        queryVariants: Set<String>,
        record: DocRecord,
    ): FieldMatch? {
        var best: FieldMatch? = null
        for ((field, values) in record.doc.fields) {
            val matcher = matcherByField[field] ?: continue
            for (value in values) {
                val tier = matcher.match(queryVariants, value) ?: continue
                if (best == null || tier.rank > best.tier.rank) {
                    best = FieldMatch(tier, field)
                } else if (tier == best.tier && field.ordinal < best.field.ordinal) {
                    best = FieldMatch(tier, field)
                }
            }
        }
        return best
    }

    private data class DocRecord(
        val doc: SearchDoc,
    )

    private data class FieldMatch(
        val tier: MatchTier,
        val field: SearchField,
    )
}

/** One matching document, with the best tier and field that matched it. */
data class DocMatch(
    val doc: SearchDoc,
    val tier: MatchTier,
    val field: SearchField,
)
