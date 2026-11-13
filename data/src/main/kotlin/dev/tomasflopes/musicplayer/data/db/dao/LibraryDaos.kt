package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.AlbumEntity
import dev.tomasflopes.musicplayer.data.db.entity.ArtistEntity
import dev.tomasflopes.musicplayer.data.db.entity.FolderEntity
import dev.tomasflopes.musicplayer.data.db.entity.SongFeatureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Insert
    suspend fun insert(artist: ArtistEntity): Long

    @Query("SELECT * FROM artist WHERE name = :name")
    suspend fun byName(name: String): ArtistEntity?

    /** Every known main artist, as name/id rows, for resolving featured-artist matches. */
    @Query("SELECT name, id FROM artist")
    suspend fun allArtistNameId(): List<ArtistNameIdRow>

    @Query(
        "SELECT artist.id, artist.name, artist.sort_name, COUNT(song.id) AS song_count, " +
            "COUNT(DISTINCT song.album_id) AS album_count FROM artist " +
            "JOIN song ON song.artist_id = artist.id AND song.in_library = 1 " +
            "GROUP BY artist.id ORDER BY artist.sort_name, artist.id",
    )
    fun observeAll(): Flow<List<ArtistRow>>

    @Query(
        "SELECT artist.id, artist.name, artist.sort_name, COUNT(song.id) AS song_count, " +
            "COUNT(DISTINCT song.album_id) AS album_count FROM artist " +
            "JOIN song ON song.artist_id = artist.id AND song.in_library = 1 WHERE artist.id = :id GROUP BY artist.id",
    )
    fun observe(id: Long): Flow<ArtistRow?>
}

data class ArtistRow(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    @ColumnInfo(name = "song_count") val songCount: Int,
    @ColumnInfo(name = "album_count") val albumCount: Int,
)

data class AlbumRow(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "album_artist") val albumArtist: String,
    @ColumnInfo(name = "song_count") val songCount: Int,
    @ColumnInfo(name = "cover_song_id") val coverSongId: Long?,
)

data class ArtistNameIdRow(
    val name: String,
    val id: Long,
)

private const val ALBUM_ROWS =
    "SELECT album.id, album.title, album.album_artist, COUNT(song.id) AS song_count, " +
        "(SELECT s.id FROM song s WHERE s.album_id = album.id AND s.in_library = 1 AND s.has_cover = 1 " +
        "ORDER BY COALESCE(s.disc_number, 1), s.track_number LIMIT 1) AS cover_song_id FROM album " +
        "JOIN song ON song.album_id = album.id AND song.in_library = 1"

@Dao
interface AlbumDao {
    @Insert
    suspend fun insert(album: AlbumEntity): Long

    @Query("SELECT * FROM album WHERE title = :title AND album_artist = :albumArtist")
    suspend fun byKey(
        title: String,
        albumArtist: String,
    ): AlbumEntity?

    @Query("$ALBUM_ROWS GROUP BY album.id ORDER BY album.sort_title, album.album_artist, album.id")
    fun observeAll(): Flow<List<AlbumRow>>

    @Query("$ALBUM_ROWS WHERE album.id = :id GROUP BY album.id")
    fun observe(id: Long): Flow<AlbumRow?>

    @Query(
        "$ALBUM_ROWS WHERE album.id IN " +
            "(SELECT DISTINCT album_id FROM song WHERE artist_id = :artistId AND in_library = 1) " +
            "GROUP BY album.id ORDER BY album.sort_title, album.id",
    )
    fun observeByArtist(artistId: Long): Flow<List<AlbumRow>>
}

@Dao
interface FolderDao {
    @Insert
    suspend fun insert(folder: FolderEntity)

    @Query("SELECT * FROM folder WHERE path = :path")
    suspend fun byPath(path: String): FolderEntity?

    @Query(
        "SELECT folder.path, folder.name, folder.parent_path, " +
            "(SELECT COUNT(*) FROM song WHERE song.in_library = 1 AND " +
            "(song.folder_path = folder.path OR song.folder_path LIKE folder.path || '/%')) AS song_count " +
            "FROM folder WHERE folder.parent_path IS :parentPath ORDER BY folder.name",
    )
    fun observeChildren(parentPath: String?): Flow<List<FolderRow>>
}

data class FolderRow(
    val path: String,
    val name: String,
    @ColumnInfo(name = "parent_path") val parentPath: String?,
    @ColumnInfo(name = "song_count") val songCount: Int,
)

@Dao
interface SongFeatureDao {
    @Insert
    suspend fun insert(feature: SongFeatureEntity): Long

    @Query("DELETE FROM song_feature")
    suspend fun deleteAll()

    /** The display name of every featured artist on a song: the main artist when matched, else the raw text. */
    @Query(
        "SELECT COALESCE(artist.name, song_feature.raw_name) AS name FROM song_feature " +
            "LEFT JOIN artist ON artist.id = song_feature.artist_id WHERE song_feature.song_id = :songId",
    )
    suspend fun namesFor(songId: Long): List<String>
}
