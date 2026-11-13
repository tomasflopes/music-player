package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.data.db.entity.SongEntity
import dev.tomasflopes.musicplayer.data.scan.KnownSong

/** What the scan needs to know about every song ever seen, retired ones included. */
data class KnownSongRow(
    val id: Long,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "volume_name") val volumeName: String,
    val path: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "file_modified") val fileModified: Long,
    @ColumnInfo(name = "in_library") val inLibrary: Boolean,
) {
    fun toKnownSong() = KnownSong(SongId(id), mediaStoreId, volumeName, path, fileSize, fileModified, inLibrary)
}

/** The scan's writes. They never touch `date_added` and never delete a row (plan C4). */
@Dao
interface SongScanDao {
    @Insert
    suspend fun insert(song: SongEntity): Long

    @Query("SELECT id, media_store_id, volume_name, path, file_size, file_modified, in_library FROM song")
    suspend fun knownSongs(): List<KnownSongRow>

    /** Re-read tags: everything but id, date_added and in_library comes from [song]. */
    @Query(
        "UPDATE song SET media_store_id = :mediaStoreId, path = :path, file_name = :fileName, " +
            "folder_path = :folderPath, title = :title, sort_title = :sortTitle, artist_id = :artistId, " +
            "album_id = :albumId, album_artist = :albumArtist, track_number = :trackNumber, " +
            "disc_number = :discNumber, duration_ms = :durationMs, file_size = :fileSize, " +
            "file_modified = :fileModified, has_cover = :hasCover, tag_version = :tagVersion WHERE id = :id",
    )
    suspend fun updateTags(
        id: Long,
        mediaStoreId: Long,
        path: String,
        fileName: String,
        folderPath: String,
        title: String,
        sortTitle: String,
        artistId: Long,
        albumId: Long?,
        albumArtist: String?,
        trackNumber: Int?,
        discNumber: Int?,
        durationMs: Long,
        fileSize: Long,
        fileModified: Long,
        hasCover: Boolean,
        tagVersion: String,
    )

    @Query(
        "UPDATE song SET path = :path, file_name = :fileName, folder_path = :folderPath, " +
            "media_store_id = :mediaStoreId WHERE id = :id",
    )
    suspend fun move(
        id: Long,
        path: String,
        fileName: String,
        folderPath: String,
        mediaStoreId: Long,
    )

    @Query("UPDATE song SET media_store_id = :mediaStoreId WHERE id = :id")
    suspend fun refreshMediaStoreId(
        id: Long,
        mediaStoreId: Long,
    )

    @Query("UPDATE song SET in_library = :inLibrary WHERE id = :id")
    suspend fun setInLibrary(
        id: Long,
        inLibrary: Boolean,
    )
}
