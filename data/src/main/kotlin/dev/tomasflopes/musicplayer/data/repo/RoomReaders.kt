package dev.tomasflopes.musicplayer.data.repo

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.AlbumOrder
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Folder
import dev.tomasflopes.musicplayer.core.model.NaturalOrder
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongArtists
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaylistAddEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchEvent
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.core.model.ports.FolderReader
import dev.tomasflopes.musicplayer.core.model.ports.LibraryChanges
import dev.tomasflopes.musicplayer.core.model.ports.PlayEventSource
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryReader
import dev.tomasflopes.musicplayer.core.model.ports.PlayHistoryWriter
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSongSource
import dev.tomasflopes.musicplayer.core.model.ports.PlaylistAddSource
import dev.tomasflopes.musicplayer.core.model.ports.SearchEventSource
import dev.tomasflopes.musicplayer.core.model.ports.SearchHistoryWriter
import dev.tomasflopes.musicplayer.core.model.ports.SongArtistSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.dao.SongRow
import dev.tomasflopes.musicplayer.data.db.entity.PlayEventEntity
import dev.tomasflopes.musicplayer.data.db.entity.SearchEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private fun Flow<List<SongRow>>.songs(): Flow<List<Song>> = map { rows -> rows.map(SongRow::toSong) }

private val BY_FILE_NAME = compareBy(NaturalOrder, Song::fileName)

/** Fires on every write to the `song` table, which is where a scan adds, retires and revives songs (ADR 0034). */
class RoomLibraryChanges(
    private val db: MusicDatabase,
) : LibraryChanges {
    override fun observe(): Flow<Unit> = db.invalidationTracker.createFlow("song").map { }
}

/** `:core:model` ports over Room. The SQL does the heavy sorting; the domain comparators settle the rest. */
class RoomSongReader(
    private val db: MusicDatabase,
) : SongReader {
    override fun observeSongs(): Flow<List<Song>> = db.songDao().observeAll().songs()

    override fun observeAddedSince(epochSec: Long): Flow<List<Song>> = db.songDao().observeAddedSince(epochSec).songs()

    override fun observeAlbumSongs(id: AlbumId): Flow<List<Song>> =
        db
            .songDao()
            .observeAlbumSongs(id.value)
            .songs()
            .map { it.sortedWith(AlbumOrder) }

    override fun observeArtistSongs(id: ArtistId): Flow<List<Song>> = db.songDao().observeArtistSongs(id.value).songs()

    override fun observeFeaturedOn(id: ArtistId): Flow<List<Song>> = db.songDao().observeFeaturedOn(id.value).songs()

    override fun observeFolderSongs(folderPath: String): Flow<List<Song>> =
        db
            .songDao()
            .observeFolderSongs(folderPath)
            .songs()
            .map { songs -> songs.sortedWith(BY_FILE_NAME) }

    override suspend fun songsByIds(ids: List<SongId>): List<Song> {
        val byId = db.songDao().songsByIds(ids.map { it.value }).associateBy { it.song.id }
        return ids.mapNotNull { byId[it.value]?.toSong() }
    }

    override suspend fun allSongRefs(): List<SongRef> = db.songDao().allSongRefs().map { it.toSongRef() }
}

class RoomArtistReader(
    private val db: MusicDatabase,
) : ArtistReader {
    override fun observeArtists(): Flow<List<Artist>> =
        db.artistDao().observeAll().map { rows -> rows.map { it.toArtist() } }

    override fun observeArtist(id: ArtistId): Flow<Artist?> = db.artistDao().observe(id.value).map { it?.toArtist() }
}

class RoomAlbumReader(
    private val db: MusicDatabase,
) : AlbumReader {
    override fun observeAlbums(): Flow<List<Album>> =
        db.albumDao().observeAll().map { rows -> rows.map { it.toAlbum() } }

    override fun observeAlbum(id: AlbumId): Flow<Album?> = db.albumDao().observe(id.value).map { it?.toAlbum() }

    override fun observeArtistAlbums(id: ArtistId): Flow<List<Album>> =
        db.albumDao().observeByArtist(id.value).map { rows -> rows.map { it.toAlbum() } }
}

class RoomFolderReader(
    private val db: MusicDatabase,
) : FolderReader {
    override fun observeChildren(parentPath: String?): Flow<List<Folder>> =
        db.folderDao().observeChildren(parentPath).map { rows -> rows.map { it.toFolder() } }
}

class RoomPlayHistory(
    private val db: MusicDatabase,
) : PlayHistoryWriter,
    PlayHistoryReader,
    PlayEventSource,
    PlayedSongSource {
    override suspend fun append(event: PlayEvent) = db.playEventDao().insert(PlayEventEntity.from(event))

    override fun observeSince(sinceEpochMs: Long): Flow<List<PlayEvent>> =
        db.playEventDao().observeSince(sinceEpochMs).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<PlayEvent>> =
        db.playEventDao().observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeCount(): Flow<Int> = db.playEventDao().observeCount()

    override fun observePlayedIds(): Flow<Set<SongId>> =
        db
            .playEventDao()
            .observePlayedSongIds()
            .map { ids -> ids.mapTo(HashSet()) { SongId(it) } }
            .distinctUntilChanged()
}

class RoomSearchHistory(
    private val db: MusicDatabase,
) : SearchHistoryWriter,
    SearchEventSource {
    override suspend fun append(event: SearchEvent) = db.searchEventDao().insert(SearchEventEntity.from(event))

    override fun observeSince(sinceEpochMs: Long): Flow<List<SearchEvent>> =
        db.searchEventDao().observeSince(sinceEpochMs).map { rows -> rows.map { it.toDomain() } }
}

class RoomPlaylistAdds(
    private val db: MusicDatabase,
) : PlaylistAddSource {
    override fun observeSince(sinceEpochMs: Long): Flow<List<PlaylistAddEvent>> =
        db.playlistAddEventDao().observeSince(sinceEpochMs).map { rows -> rows.map { it.toDomain() } }
}

class RoomSongArtists(
    private val db: MusicDatabase,
) : SongArtistSource {
    override suspend fun artistsOf(songs: Collection<SongId>): Map<SongId, SongArtists> {
        val dao = db.songArtistDao()
        val result = HashMap<SongId, SongArtists>()
        songs.map { it.value }.distinct().chunked(CHUNK).forEach { ids ->
            val featured = dao.featuredArtists(ids).groupBy({ it.songId }, { ArtistId(it.artistId) })
            dao.mainArtists(ids).forEach { row ->
                result[SongId(row.id)] = SongArtists(ArtistId(row.artistId), featured[row.id].orEmpty())
            }
        }
        return result
    }

    private companion object {
        /** Below SQLite's limit on bound parameters. */
        const val CHUNK = 900
    }
}
