package dev.tomasflopes.musicplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One MP3 the scan has ever seen. Never deleted: a file that leaves the music folder gets `in_library = 0`
 * and keeps its id, so its history comes back with it (plan C4).
 */
@Entity(
    tableName = "song",
    indices = [
        Index(value = ["volume_name", "path"], unique = true),
        Index("artist_id"),
        Index("album_id"),
        Index("folder_path"),
        Index("date_added"),
        Index("sort_title"),
    ],
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "volume_name") val volumeName: String,
    val path: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "folder_path") val folderPath: String,
    val title: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "album_id") val albumId: Long?,
    @ColumnInfo(name = "album_artist") val albumArtist: String?,
    @ColumnInfo(name = "track_number") val trackNumber: Int?,
    @ColumnInfo(name = "disc_number") val discNumber: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "file_modified") val fileModified: Long,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "has_cover") val hasCover: Boolean,
    @ColumnInfo(name = "tag_version") val tagVersion: String,
    @ColumnInfo(name = "in_library") val inLibrary: Boolean,
) {
    /** Rebuilt from the volume and MediaStore id, so the row stays correct when MediaStore re-indexes. */
    fun contentUri(): String = "content://media/$volumeName/audio/media/$mediaStoreId"
}

@Entity(tableName = "artist", indices = [Index(value = ["name"], unique = true)])
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
)

@Entity(tableName = "album", indices = [Index(value = ["title", "album_artist"], unique = true)])
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "album_artist") val albumArtist: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String,
)

@Entity(tableName = "folder", indices = [Index("parent_path")])
data class FolderEntity(
    @PrimaryKey val path: String,
    val name: String,
    @ColumnInfo(name = "parent_path") val parentPath: String?,
)

/** A featured artist parsed from a song title; [artistId] is set when the name is a main artist in the library. */
@Entity(tableName = "song_feature", indices = [Index("song_id"), Index("artist_id")])
data class SongFeatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long?,
    @ColumnInfo(name = "raw_name") val rawName: String?,
)
