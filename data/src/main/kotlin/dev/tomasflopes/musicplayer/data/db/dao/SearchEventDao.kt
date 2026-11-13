package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.SearchEventEntity

/** Chosen search results. Insert and read only (LogIsAppendOnlyTest enforces it). */
@Dao
interface SearchEventDao {
    @Insert
    suspend fun insert(event: SearchEventEntity)

    @Query("SELECT COUNT(*) FROM search_event")
    suspend fun count(): Int
}
