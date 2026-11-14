package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.PlaylistDismissedEntity
import kotlinx.coroutines.flow.Flow

/** Suggestions dismissed per playlist (ADR 0051). */
@Dao
interface PlaylistDismissedDao {
    /** Dismissing twice keeps the first row. The playlist must exist (foreign key). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: PlaylistDismissedEntity)

    @Query("SELECT COUNT(*) FROM playlist WHERE id = :playlistId")
    suspend fun playlistCount(playlistId: Long): Int

    @Query("SELECT song_id FROM playlist_dismissed WHERE playlist_id = :playlistId")
    fun observeSongIds(playlistId: Long): Flow<List<Long>>
}
