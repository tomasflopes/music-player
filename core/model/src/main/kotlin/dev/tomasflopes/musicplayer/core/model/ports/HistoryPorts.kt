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

/** The listening log as input for scoring. */
interface PlayEventSource {
    /**
     * Rows that started at or after [sinceEpochMs], oldest first, rows with equal times in the order they were
     * logged. Emits again whenever the log changes.
     */
    fun observeSince(sinceEpochMs: Long): Flow<List<PlayEvent>>
}

interface SearchHistoryWriter {
    suspend fun append(event: SearchEvent)
}
