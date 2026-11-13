package dev.tomasflopes.musicplayer.fakes

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.ports.LibraryScanController
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.ScanPhase
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.playback.PlaybackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** Records what screens ask the player to do; it never plays anything. */
class FakePlaybackController : PlaybackController {
    override val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())
    val requests = mutableListOf<PlayRequest>()

    override fun play(request: PlayRequest) {
        requests += request
    }

    override fun playNext(songs: List<SongRef>) = Unit

    override fun addToQueue(songs: List<SongRef>) = Unit

    override fun togglePlayPause() = Unit

    override fun next() = Unit

    override fun previous() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun jumpToUpcoming(index: Int) = Unit

    override fun setShuffle(on: Boolean) = Unit

    override fun cycleRepeatMode() = Unit
}

/** A library that is just [songs] (and their [refs]); the queries a screen under test does not use are empty. */
class FakeSongReader(
    private val refs: List<SongRef> = emptyList(),
    private val songs: List<Song> = emptyList(),
) : SongReader {
    override fun observeSongs(): Flow<List<Song>> = flowOf(songs)

    override fun observeAddedSince(epochSec: Long): Flow<List<Song>> = emptyFlow()

    override fun observeAlbumSongs(id: AlbumId): Flow<List<Song>> = emptyFlow()

    override fun observeArtistSongs(id: ArtistId): Flow<List<Song>> = emptyFlow()

    override fun observeFeaturedOn(id: ArtistId): Flow<List<Song>> = emptyFlow()

    override fun observeFolderSongs(folderPath: String): Flow<List<Song>> = emptyFlow()

    override suspend fun songsByIds(ids: List<SongId>): List<Song> = emptyList()

    override suspend fun allSongRefs(): List<SongRef> = refs
}

class FakeSettingsRepository : SettingsRepository {
    val root = MutableStateFlow<MusicRoot?>(null)
    override val musicRoot: Flow<MusicRoot?> = root
    override val recentlyAddedDays: Flow<Int> = MutableStateFlow(20)

    override suspend fun setMusicRoot(root: MusicRoot) {
        this.root.value = root
    }

    override suspend fun setRecentlyAddedDays(days: Int) = Unit
}

class FakeScanController : LibraryScanController {
    override val progress: StateFlow<ScanProgress> = MutableStateFlow(ScanProgress(ScanPhase.IDLE, 0, 0))
    var scanRequests = 0

    override fun requestScan() {
        scanRequests++
    }
}
