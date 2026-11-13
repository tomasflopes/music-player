package dev.tomasflopes.musicplayer.ui.search

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType
import dev.tomasflopes.musicplayer.core.search.SearchConfig
import dev.tomasflopes.musicplayer.core.search.SearchHit
import dev.tomasflopes.musicplayer.core.search.SearchIndex
import dev.tomasflopes.musicplayer.core.search.SearchResults
import dev.tomasflopes.musicplayer.core.search.Searcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One consistent read of the library, which is everything a search index is built from. */
data class LibrarySnapshot(
    val artists: List<Artist>,
    val albums: List<Album>,
    val songs: List<Song>,
    val features: Map<SongId, List<String>>,
)

/**
 * A search over one [LibrarySnapshot]. Immutable once built: a library change builds a new engine instead of
 * touching this one, so a keystroke never races a rebuild.
 */
interface SearchEngine {
    suspend fun search(query: String): SearchResults

    /** "See all": every match of [type], mid-word matches last. */
    suspend fun searchAll(
        query: String,
        type: SearchItemType,
    ): List<SearchHit>

    /** A song of the snapshot, used to find the cover of an album hit. */
    fun song(id: SongId): Song?
}

/** The decision point for the search path (CLAUDE.md): another engine can be benchmarked beside the core one. */
fun interface SearchEngineFactory {
    suspend fun build(snapshot: LibrarySnapshot): SearchEngine
}

/** Builds and queries the pure-Kotlin `:core:search` index on [dispatcher], never on the main thread. */
class CoreSearchEngineFactory(
    private val config: SearchConfig = SearchConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SearchEngineFactory {
    override suspend fun build(snapshot: LibrarySnapshot): SearchEngine =
        withContext(dispatcher) {
            val index = SearchIndex()
            index.rebuild(snapshot.artists, snapshot.albums, snapshot.songs, snapshot.features)
            CoreSearchEngine(index, Searcher(index, config), dispatcher)
        }
}

private class CoreSearchEngine(
    private val index: SearchIndex,
    private val searcher: Searcher,
    private val dispatcher: CoroutineDispatcher,
) : SearchEngine {
    override suspend fun search(query: String): SearchResults = withContext(dispatcher) { searcher.search(query) }

    override suspend fun searchAll(
        query: String,
        type: SearchItemType,
    ): List<SearchHit> = withContext(dispatcher) { searcher.searchAll(query, type) }

    override fun song(id: SongId): Song? = index.song(id.value)
}
