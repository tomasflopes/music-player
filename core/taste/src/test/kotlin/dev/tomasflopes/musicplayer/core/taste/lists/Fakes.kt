package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ref
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal fun song(
    id: Long,
    addedEpochSec: Long,
) = Song(
    id = SongId(id),
    title = "Song $id",
    artistId = ArtistId(1),
    artistName = "Artist",
    albumId = null,
    albumTitle = null,
    albumArtist = null,
    trackNumber = null,
    discNumber = null,
    durationMs = 1_000,
    volumeName = "external_primary",
    path = "Music/$id.mp3",
    fileName = "$id.mp3",
    folderPath = "Music",
    dateAddedEpochSec = addedEpochSec,
    contentUri = "content://media/external/audio/media/$id",
    hasCover = false,
)

/** Answers only the queries the dynamic lists use, over a fixed list of songs. */
internal class FakeSongReader(
    private val songs: List<Song>,
) : SongReader {
    val addedSinceCalls = mutableListOf<Long>()

    override fun observeAddedSince(epochSec: Long): Flow<List<Song>> {
        addedSinceCalls += epochSec
        return flowOf(songs.filter { it.dateAddedEpochSec >= epochSec }.sortedByDescending { it.dateAddedEpochSec })
    }

    override fun observeSongs(): Flow<List<Song>> = flowOf(songs)

    override fun observeAlbumSongs(id: AlbumId): Flow<List<Song>> = flowOf(emptyList())

    override fun observeArtistSongs(id: ArtistId): Flow<List<Song>> = flowOf(emptyList())

    override fun observeFeaturedOn(id: ArtistId): Flow<List<Song>> = flowOf(emptyList())

    override fun observeFolderSongs(folderPath: String): Flow<List<Song>> = flowOf(emptyList())

    override suspend fun songsByIds(ids: List<SongId>): List<Song> = ids.mapNotNull { id -> songs.find { it.id == id } }

    override suspend fun allSongRefs(): List<SongRef> = songs.map { it.ref }
}

internal class FakeSettings(
    days: Int,
) : SettingsRepository {
    private val days = MutableStateFlow(days)
    private val root = MutableStateFlow<MusicRoot?>(null)

    override val musicRoot: Flow<MusicRoot?> = root
    override val recentlyAddedDays: Flow<Int> = this.days.map { it }

    override suspend fun setMusicRoot(root: MusicRoot) {
        this.root.value = root
    }

    override suspend fun setRecentlyAddedDays(days: Int) {
        this.days.value = days
    }
}
