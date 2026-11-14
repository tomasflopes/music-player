package dev.tomasflopes.musicplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved playlist (design 7.12). [lastUsedAt] orders the "add to playlist" sheet, most recent first. */
@Entity(tableName = "playlist", indices = [Index("last_used_at")])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
)

/**
 * One song in a playlist. A song is in a playlist at most once (the primary key), and [songId] has no foreign key:
 * songs are never deleted (ADR 0021), only retired, and a retired song keeps its place so it comes back with the
 * file. Readers join `song` and show only `in_library = 1` rows; [position] may therefore have gaps.
 */
@Entity(
    tableName = "playlist_song",
    primaryKeys = ["playlist_id", "song_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("song_id"), Index(value = ["playlist_id", "position"])],
)
data class PlaylistSongEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "song_id") val songId: Long,
    val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)
