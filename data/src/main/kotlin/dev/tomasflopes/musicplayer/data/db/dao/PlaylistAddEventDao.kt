package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.PlaylistAddEventEntity
import kotlinx.coroutines.flow.Flow

/** Songs added to playlists. Insert and read only (LogIsAppendOnlyTest enforces it). */
@Dao
interface PlaylistAddEventDao {
    @Insert
    suspend fun insertAll(events: List<PlaylistAddEventEntity>)

    /** Oldest first; ties keep the order the rows were logged in. */
    @Query("SELECT * FROM playlist_add_event WHERE at >= :since ORDER BY at, id")
    fun observeSince(since: Long): Flow<List<PlaylistAddEventEntity>>

    @Query("SELECT COUNT(*) FROM playlist_add_event")
    suspend fun count(): Int
}
