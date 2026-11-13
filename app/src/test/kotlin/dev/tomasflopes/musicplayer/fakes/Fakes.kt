package dev.tomasflopes.musicplayer.fakes

import androidx.compose.runtime.Composable
import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.SearchEvent
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.core.model.ports.LibraryScanController
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.ScanPhase
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import dev.tomasflopes.musicplayer.core.model.ports.SearchHistoryWriter
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongFeatureReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.playback.PlaybackState
import dev.tomasflopes.musicplayer.ui.home.HomeSection
import dev.tomasflopes.musicplayer.ui.settings.SettingsEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Records what screens ask the player to do; it never plays anything. A test models the player changing by setting
 * [playbackState], which [state] follows.
 */
class FakePlaybackController : PlaybackController {
    val playbackState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = playbackState
    val requests = mutableListOf<PlayRequest>()

    /** Every "Play next" and "Add to queue" call, in order, with the songs it carried. */
    val playNextCalls = mutableListOf<List<SongRef>>()
    val addToQueueCalls = mutableListOf<List<SongRef>>()

    /** Transport calls: how often each button was pressed, and the arguments of the ones that take one. */
    var togglePlayPauseCalls = 0
    var nextCalls = 0
    var previousCalls = 0
    var repeatCycles = 0
    val seeks = mutableListOf<Long>()
    val jumps = mutableListOf<Int>()
    val shuffleCalls = mutableListOf<Boolean>()

    override fun play(request: PlayRequest) {
        requests += request
    }

    override fun playNext(songs: List<SongRef>) {
        playNextCalls += songs
    }

    override fun addToQueue(songs: List<SongRef>) {
        addToQueueCalls += songs
    }

    override fun togglePlayPause() {
        togglePlayPauseCalls++
    }

    override fun next() {
        nextCalls++
    }

    override fun previous() {
        previousCalls++
    }

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
    }

    override fun jumpToUpcoming(index: Int) {
        jumps += index
    }

    override fun setShuffle(on: Boolean) {
        shuffleCalls += on
    }

    override fun cycleRepeatMode() {
        repeatCycles++
    }
}

/**
 * A library that is just [songs] (and their [refs]). [library] can be re-emitted to model a library change.
 * [artistSongs], [albumSongs] and [featuredOn] answer the per-artist, per-album and featured-on queries (a test gives
 * them in the order the real reader would), [songsByIds] looks in [library]; the others are empty.
 */
class FakeSongReader(
    private val refs: List<SongRef> = emptyList(),
    songs: List<Song> = emptyList(),
    private val artistSongs: Map<ArtistId, List<Song>> = emptyMap(),
    private val albumSongs: Map<AlbumId, List<Song>> = emptyMap(),
    private val featuredOn: Map<ArtistId, List<Song>> = emptyMap(),
) : SongReader {
    val library = MutableStateFlow(songs)

    override fun observeSongs(): Flow<List<Song>> = library

    override fun observeAddedSince(epochSec: Long): Flow<List<Song>> =
        library.map { list ->
            list
                .filter { it.dateAddedEpochSec >= epochSec }
                .sortedByDescending { it.dateAddedEpochSec }
        }

    override fun observeAlbumSongs(id: AlbumId): Flow<List<Song>> = flowOf(albumSongs[id].orEmpty())

    override fun observeArtistSongs(id: ArtistId): Flow<List<Song>> = flowOf(artistSongs[id].orEmpty())

    override fun observeFeaturedOn(id: ArtistId): Flow<List<Song>> = flowOf(featuredOn[id].orEmpty())

    override fun observeFolderSongs(folderPath: String): Flow<List<Song>> = emptyFlow()

    override suspend fun songsByIds(ids: List<SongId>): List<Song> {
        val byId = library.value.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    override suspend fun allSongRefs(): List<SongRef> = refs
}

/** [library] can be re-emitted to model a library change. */
class FakeArtistReader(
    artists: List<Artist> = emptyList(),
) : ArtistReader {
    val library = MutableStateFlow(artists)

    override fun observeArtists(): Flow<List<Artist>> = library

    override fun observeArtist(id: ArtistId): Flow<Artist?> = library.map { list -> list.firstOrNull { it.id == id } }
}

/** [library] can be re-emitted to model a library change; [artistAlbums] answers the per-artist query. */
class FakeAlbumReader(
    albums: List<Album> = emptyList(),
    private val artistAlbums: Map<ArtistId, List<Album>> = emptyMap(),
) : AlbumReader {
    val library = MutableStateFlow(albums)

    override fun observeAlbums(): Flow<List<Album>> = library

    override fun observeAlbum(id: AlbumId): Flow<Album?> = library.map { list -> list.firstOrNull { it.id == id } }

    override fun observeArtistAlbums(id: ArtistId): Flow<List<Album>> = flowOf(artistAlbums[id].orEmpty())
}

/** Featured artists by song, as the scanner would have written them. */
class FakeSongFeatureReader(
    var features: Map<SongId, List<String>> = emptyMap(),
) : SongFeatureReader {
    override suspend fun namesFor(songId: SongId): List<String> = features[songId].orEmpty()

    override suspend fun all(): Map<SongId, List<String>> = features
}

/** Keeps every appended search event, in order. */
class FakeSearchHistoryWriter : SearchHistoryWriter {
    val events = mutableListOf<SearchEvent>()

    override suspend fun append(event: SearchEvent) {
        events += event
    }
}

class FakeSettingsRepository : SettingsRepository {
    val root = MutableStateFlow<MusicRoot?>(null)
    override val musicRoot: Flow<MusicRoot?> = root

    /** The saved window; [setRecentlyAddedDays] changes it, as the real store does. */
    val days = MutableStateFlow(20)
    override val recentlyAddedDays: Flow<Int> = days

    /** Every value a screen asked to save, in order. */
    val savedDays = mutableListOf<Int>()

    override suspend fun setMusicRoot(root: MusicRoot) {
        this.root.value = root
    }

    override suspend fun setRecentlyAddedDays(days: Int) {
        savedDays += days
        this.days.value = days
    }
}

/** A dynamic list whose songs a test can change through [list]. */
class FakeDynamicListProvider(
    override val id: String,
    override val order: Int = 0,
    override val title: String = id,
    override val playSource: PlaySource = PlaySource.MIX,
    songs: List<Song> = emptyList(),
) : DynamicListProvider {
    val list = MutableStateFlow(songs)

    override fun songs(): Flow<List<Song>> = list
}

/** A Home section that draws nothing; tests look at which sections Home would show and in what order. */
class FakeHomeSection(
    override val id: String,
    override val order: Int = 0,
) : HomeSection {
    @Composable
    override fun Content(onNavigate: (String) -> Unit) = Unit
}

/** A Settings row that draws nothing; tests look at which entries Settings would show and in what order. */
class FakeSettingsEntry(
    override val id: String,
    override val order: Int = 0,
) : SettingsEntry {
    @Composable
    override fun Content() = Unit
}

class FakeScanController : LibraryScanController {
    override val progress: StateFlow<ScanProgress> = MutableStateFlow(ScanProgress(ScanPhase.IDLE, 0, 0))
    var scanRequests = 0

    override fun requestScan() {
        scanRequests++
    }
}
