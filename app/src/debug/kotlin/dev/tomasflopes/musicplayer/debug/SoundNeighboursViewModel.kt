package dev.tomasflopes.musicplayer.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.core.model.ports.SongFeatureReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.core.model.sound.SoundNeighbours
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.ui.search.LibrarySnapshot
import dev.tomasflopes.musicplayer.ui.search.SearchEngine
import dev.tomasflopes.musicplayer.ui.search.SearchEngineFactory
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One neighbour of the seed: the song, its distance and the five weighted, normalised values. */
class NeighbourRow(
    val song: Song,
    val distance: Double,
    val values: FloatArray,
)

data class SoundNeighboursUiState(
    val query: String = "",
    /** Songs the search found for [query]; empty once a seed is picked. */
    val matches: List<Song> = emptyList(),
    val seed: Song? = null,
    val seedValues: FloatArray = FloatArray(0),
    val neighbours: List<NeighbourRow> = emptyList(),
    val message: String = "",
)

/**
 * Debug-only (ADR 0019, 0073): pick a seed through the same search engine as the Search tab, then list the 20 songs
 * that sound closest to it, from the same [SoundVectorSource] Discover will use. Tapping a neighbour plays it.
 */
@Suppress("LongParameterList")
@HiltViewModel
class SoundNeighboursViewModel
    @Inject
    constructor(
        private val engineFactory: SearchEngineFactory,
        private val songReader: SongReader,
        private val artistReader: ArtistReader,
        private val albumReader: AlbumReader,
        private val featureReader: SongFeatureReader,
        private val soundSource: SoundVectorSource,
        private val playback: PlaybackController,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(SoundNeighboursUiState())
        val state: StateFlow<SoundNeighboursUiState> = mutableState.asStateFlow()

        private var songsById: Map<Long, Song> = emptyMap()
        private var searchJob: Job? = null

        private val engine: Deferred<SearchEngine> =
            viewModelScope.async(start = CoroutineStart.LAZY) {
                val songs = songReader.observeSongs().first()
                songsById = songs.associateBy { it.id.value }
                val snapshot =
                    LibrarySnapshot(
                        artistReader.observeArtists().first(),
                        albumReader.observeAlbums().first(),
                        songs,
                        featureReader.all(),
                    )
                engineFactory.build(snapshot)
            }

        fun onQueryChange(text: String) {
            mutableState.value = SoundNeighboursUiState(query = text)
            searchJob?.cancel()
            if (text.isBlank()) return
            searchJob =
                viewModelScope.launch {
                    val songs =
                        engine
                            .await()
                            .search(text)
                            .songs
                            .map { it.song }
                            .take(MAX_MATCHES)
                    mutableState.value = mutableState.value.copy(matches = songs)
                }
        }

        fun onSeedPicked(song: Song) {
            viewModelScope.launch {
                mutableState.value = mutableState.value.copy(matches = emptyList(), seed = song, message = "Working")
                mutableState.value = withContext(Dispatchers.Default) { neighboursOf(song) }
            }
        }

        /** Plays [index] of the neighbours shown; the ones after it follow in order. */
        fun onNeighbourClick(index: Int) {
            val rows = mutableState.value.neighbours
            if (index !in rows.indices) return
            val refs = rows.map { it.song.ref }
            playback.play(PlayRequest(refs, index, shuffle = false, PlaySource.DISCOVER, picked = true))
        }

        private suspend fun neighboursOf(seed: Song): SoundNeighboursUiState {
            val shown = mutableState.value.copy(message = "")
            val space = soundSource.space()
            val row = space.indexOf(seed.id.value)
            if (row < 0) return shown.copy(message = "No sound profile for this song yet.")
            val keyOf = { id: Long -> songsById[id]?.let { SoundNeighbours.fileKey(it.fileName) } }
            val rows =
                SoundNeighbours.nearest(space, row, NEIGHBOURS, keyOf).mapNotNull { n ->
                    songsById[n.songId]?.let { NeighbourRow(it, n.distance, space.vector(n.row)) }
                }
            return shown.copy(seedValues = space.vector(row), neighbours = rows)
        }

        private companion object {
            const val MAX_MATCHES = 30
            const val NEIGHBOURS = 20
        }
    }
