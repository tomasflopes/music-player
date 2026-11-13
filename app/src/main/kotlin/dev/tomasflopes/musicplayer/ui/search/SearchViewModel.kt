package dev.tomasflopes.musicplayer.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.SearchEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.core.model.ports.SearchHistoryWriter
import dev.tomasflopes.musicplayer.core.model.ports.SongFeatureReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.core.search.AlbumHit
import dev.tomasflopes.musicplayer.core.search.ArtistHit
import dev.tomasflopes.musicplayer.core.search.SearchHit
import dev.tomasflopes.musicplayer.core.search.SearchResults
import dev.tomasflopes.musicplayer.core.search.SongHit
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * The Search tab (design 7.9, ADR 0012, ADR 0017).
 *
 * The index is rebuilt off the main thread whenever artists, albums or songs change, and every keystroke
 * searches the latest build. Choosing any result appends a [SearchEvent] and starts playback.
 *
 * The constructor is a Hilt dependency list (the ports, the clock and the engine seam), hence the suppression.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val playback: PlaybackController,
        private val songReader: SongReader,
        artistReader: ArtistReader,
        albumReader: AlbumReader,
        private val featureReader: SongFeatureReader,
        private val history: SearchHistoryWriter,
        private val clock: Clock,
        private val engineFactory: SearchEngineFactory,
    ) : ViewModel() {
        private val queryText = MutableStateFlow("")
        private val seeAllType = MutableStateFlow<SearchItemType?>(null)

        /** The text field's content, kept apart from [state] so typing never waits for a search. */
        val query: StateFlow<String> = queryText.asStateFlow()

        // Featured artists have no change signal of their own and are rewritten at the end of a scan, so a short
        // debounce lets that write land before the index reads it. Lazily: the index is kept while the tab's
        // ViewModel lives, so switching tabs does not rebuild 16k songs.
        private val engines: StateFlow<SearchEngine?> =
            combine(artistReader.observeArtists(), albumReader.observeAlbums(), songReader.observeSongs(), ::Triple)
                .debounce(REBUILD_DEBOUNCE_MS)
                .mapLatest { (artists, albums, songs) ->
                    engineFactory.build(LibrarySnapshot(artists, albums, songs, featureReader.all()))
                }.stateIn(viewModelScope, SharingStarted.Lazily, null)

        val state: StateFlow<SearchUiState> =
            combine(queryText, seeAllType, engines, ::Triple)
                .mapLatest { (text, allType, engine) -> render(text, allType, engine) }
                .stateIn(viewModelScope, WhileSubscribed, SearchUiState.Idle)

        fun onQueryChange(text: String) {
            queryText.value = text
            if (text.isBlank()) seeAllType.value = null
        }

        fun onSeeAll(type: SearchItemType) {
            seeAllType.value = type
        }

        fun onCloseSeeAll() {
            seeAllType.value = null
        }

        /** The top card's Play: an artist's or album's songs in order, or the single song. */
        fun onPlayTop(item: SearchItem) = choose(item, shuffle = false)

        fun onShuffleTop(item: SearchItem) = choose(item, shuffle = true)

        /** An artist row shuffles that artist (how the owner plays an artist); an album plays in order. */
        fun onResultClick(item: SearchItem) = choose(item, shuffle = item is ArtistItem)

        /** Plays the songs shown now (section or "See all"), starting at [index]. */
        fun onSongClick(index: Int) {
            val shown = shownSongs()
            val song = shown.getOrNull(index) ?: return
            viewModelScope.launch {
                val refs = shown.map { it.ref }
                playback.play(PlayRequest(refs, index, shuffle = false, PlaySource.SEARCH, picked = true))
                log(SongItem(song))
            }
        }

        private fun choose(
            item: SearchItem,
            shuffle: Boolean,
        ) {
            viewModelScope.launch {
                when (item) {
                    is ArtistItem -> {
                        val songs = songReader.observeArtistSongs(item.artist.id).firstOrNull().orEmpty()
                        playList(songs, shuffle, if (shuffle) PlaySource.ARTIST_SHUFFLE else PlaySource.ARTIST)
                    }
                    is AlbumItem -> {
                        val songs = songReader.observeAlbumSongs(item.album.id).firstOrNull().orEmpty()
                        playList(songs, shuffle, PlaySource.ALBUM)
                    }
                    is SongItem ->
                        playback.play(
                            PlayRequest(listOf(item.song.ref), 0, shuffle = false, PlaySource.SEARCH, picked = true),
                        )
                }
                log(item)
            }
        }

        private fun playList(
            songs: List<Song>,
            shuffle: Boolean,
            source: PlaySource,
        ) {
            if (songs.isEmpty()) return
            playback.play(PlayRequest(songs.map { it.ref }, 0, shuffle, source, picked = false))
        }

        private suspend fun log(item: SearchItem) {
            history.append(SearchEvent(queryText.value.trim(), item.type, item.id, clock.millis()))
        }

        private fun shownSongs(): List<Song> =
            when (val shown = state.value) {
                is SearchUiState.Results -> shown.sections.songs.map { it.song }
                is SearchUiState.SeeAll -> shown.items.filterIsInstance<SongItem>().map { it.song }
                else -> emptyList()
            }

        private suspend fun render(
            text: String,
            allType: SearchItemType?,
            engine: SearchEngine?,
        ): SearchUiState =
            when {
                text.isBlank() -> SearchUiState.Idle
                engine == null -> SearchUiState.Indexing
                allType != null -> {
                    val items = engine.searchAll(text, allType).map { it.toItem(engine) }
                    if (items.isEmpty()) SearchUiState.NoResults(text) else SearchUiState.SeeAll(allType, items)
                }
                else -> engine.search(text).toState(engine)
            }

        private fun SearchResults.toState(engine: SearchEngine): SearchUiState {
            val topHit = top ?: return SearchUiState.NoResults(this.query)
            return SearchUiState.Results(
                SearchSections(
                    top = topHit.toItem(engine),
                    artists = artists.map { ArtistItem(it.artist) },
                    albums = albums.map { it.toAlbumItem(engine) },
                    songs = songs.map { SongItem(it.song) },
                    moreArtists = moreArtists,
                    moreAlbums = moreAlbums,
                    moreSongs = moreSongs,
                ),
            )
        }

        private fun SearchHit.toItem(engine: SearchEngine): SearchItem =
            when (this) {
                is ArtistHit -> ArtistItem(artist)
                is AlbumHit -> toAlbumItem(engine)
                is SongHit -> SongItem(song)
            }

        private fun AlbumHit.toAlbumItem(engine: SearchEngine) = AlbumItem(album, album.coverSongId?.let(engine::song))

        private companion object {
            const val REBUILD_DEBOUNCE_MS = 300L
        }
    }
