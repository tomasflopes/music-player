package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/** A song with its artist's name and album's title. */
data class SongRow(
    @Embedded val song: SongEntity,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "album_title") val albumTitle: String?,
)

/** Just enough to build a queue or a shuffle. */
data class SongRefRow(
    val id: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
)

private const val SELECT_ROWS =
    "SELECT song.*, artist.name AS artist_name, album.title AS album_title FROM song " +
        "JOIN artist ON artist.id = song.artist_id LEFT JOIN album ON album.id = song.album_id " +
        "WHERE song.in_library = 1"

/** Every reader query shows only songs in the library (`in_library = 1`). The scan writes through [SongScanDao]. */
@Dao
interface SongDao {
    @Query("$SELECT_ROWS ORDER BY song.sort_title, song.id")
    fun observeAll(): Flow<List<SongRow>>

    @Query("$SELECT_ROWS AND song.date_added >= :epochSec ORDER BY song.date_added DESC, song.id DESC")
    fun observeAddedSince(epochSec: Long): Flow<List<SongRow>>

    /** Disc then track in SQL; the reader applies AlbumOrder on top for unnumbered tracks. */
    @Query(
        "$SELECT_ROWS AND song.album_id = :albumId " +
            "ORDER BY COALESCE(song.disc_number, 1), song.track_number IS NULL, song.track_number, song.file_name",
    )
    fun observeAlbumSongs(albumId: Long): Flow<List<SongRow>>

    @Query("$SELECT_ROWS AND song.artist_id = :artistId ORDER BY song.sort_title, song.id")
    fun observeArtistSongs(artistId: Long): Flow<List<SongRow>>

    @Query(
        "$SELECT_ROWS AND song.id IN (SELECT song_id FROM song_feature WHERE artist_id = :artistId) " +
            "ORDER BY song.sort_title, song.id",
    )
    fun observeFeaturedOn(artistId: Long): Flow<List<SongRow>>

    @Query("$SELECT_ROWS AND song.folder_path = :folderPath ORDER BY song.file_name")
    fun observeFolderSongs(folderPath: String): Flow<List<SongRow>>

    /** Any order; the reader restores the order of [ids]. */
    @Query("$SELECT_ROWS AND song.id IN (:ids)")
    suspend fun songsByIds(ids: List<Long>): List<SongRow>

    @Query("SELECT id, artist_id FROM song WHERE in_library = 1")
    suspend fun allSongRefs(): List<SongRefRow>

    /** Every in-library song's id and title, used to recompute featured artists. */
    @Query("SELECT id, title FROM song WHERE in_library = 1")
    suspend fun allSongTitles(): List<SongTitleRow>
}

data class SongTitleRow(
    val id: Long,
    val title: String,
)
