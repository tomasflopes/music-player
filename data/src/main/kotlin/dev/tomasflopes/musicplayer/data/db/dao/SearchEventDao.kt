package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.SearchEventEntity
import kotlinx.coroutines.flow.Flow

/** Chosen search results. Insert and read only (LogIsAppendOnlyTest enforces it). */
@Dao
interface SearchEventDao {
    @Insert
    suspend fun insert(event: SearchEventEntity)

    /** Oldest first; ties keep the order the rows were logged in. */
    @Query("SELECT * FROM search_event WHERE at >= :since ORDER BY at, id")
    fun observeSince(since: Long): Flow<List<SearchEventEntity>>

    @Query("SELECT COUNT(*) FROM search_event")
    suspend fun count(): Int
}
