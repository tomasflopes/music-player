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

    @Query("SELECT COUNT(*) FROM play_event")
    fun observeCount(): Flow<Int>
}
