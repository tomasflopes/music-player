package dev.tomasflopes.musicplayer.core.model.ports

import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchEvent
import kotlinx.coroutines.flow.Flow

/** Appends to the listening log. There is deliberately no way to change or remove a row. */
interface PlayHistoryWriter {
    suspend fun append(event: PlayEvent)
}

interface PlayHistoryReader {
    /** Newest first. */
    fun observeRecent(limit: Int): Flow<List<PlayEvent>>

    fun observeCount(): Flow<Int>
}

interface SearchHistoryWriter {
    suspend fun append(event: SearchEvent)
}
