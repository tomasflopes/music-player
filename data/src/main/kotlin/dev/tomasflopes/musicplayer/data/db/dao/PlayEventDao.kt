package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.PlayEventEntity
import kotlinx.coroutines.flow.Flow

/** The listening log. Insert and read only (LogIsAppendOnlyTest enforces it). */
@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(event: PlayEventEntity)

    @Query("SELECT * FROM play_event ORDER BY started_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayEventEntity>>

    /** Oldest first; [started_at] ties keep the order the rows were logged in (taste rules read neighbours). */
    @Query("SELECT * FROM play_event WHERE started_at >= :since ORDER BY started_at, id")
    fun observeSince(since: Long): Flow<List<PlayEventEntity>>

    @Query("SELECT COUNT(*) FROM play_event")
    fun observeCount(): Flow<Int>
}
