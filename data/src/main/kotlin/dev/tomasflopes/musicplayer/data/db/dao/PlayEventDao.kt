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

    /** Every song with at least one row, of any age and outcome (Discover's "never played"). */
    @Query("SELECT DISTINCT song_id FROM play_event")
    fun observePlayedSongIds(): Flow<List<Long>>

    /** Every song with a row that started at or after [since], of any outcome (Rewind's "played lately"). */
    @Query("SELECT DISTINCT song_id FROM play_event WHERE started_at >= :since")
    fun observePlayedSongIdsSince(since: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM play_event")
    fun observeCount(): Flow<Int>
}
