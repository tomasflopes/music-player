package dev.tomasflopes.musicplayer.core.model.ports

import dev.tomasflopes.musicplayer.core.model.SongArtists
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaylistAddEvent
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

/** Which songs the listening log has ever heard of, for lists that want songs never played (Discover). */
fun interface PlayedSongSource {
    /** Every song with at least one `play_event` row, of any age and any outcome. Emits again when the set changes. */
    fun observePlayedIds(): Flow<Set<SongId>>
}

/** Which songs the listening log has heard since a moment, for lists that want songs not played lately (Rewind). */
fun interface PlayedSinceSource {
    /**
     * Every song with at least one `play_event` row that started at or after [sinceEpochMs], of any outcome.
     * Emits again when the set changes.
     */
    fun observePlayedIdsSince(sinceEpochMs: Long): Flow<Set<SongId>>
}

interface SearchHistoryWriter {
    suspend fun append(event: SearchEvent)
}

/** Playlist additions as input for scoring (insert-only, like the listening log). */
fun interface PlaylistAddSource {
    /** Rows at or after [sinceEpochMs], oldest first. Emits again whenever a song is added to a playlist. */
    fun observeSince(sinceEpochMs: Long): Flow<List<PlaylistAddEvent>>
}

/** Chosen search results as input for scoring. */
fun interface SearchEventSource {
    /** Rows at or after [sinceEpochMs], oldest first. Emits again whenever a result is chosen. */
    fun observeSince(sinceEpochMs: Long): Flow<List<SearchEvent>>
}

/** Which artists a song belongs to, for rolling song signals up to artists. Implemented by `:data`. */
fun interface SongArtistSource {
    /** Songs the library does not know (never scanned) are left out of the result. */
    suspend fun artistsOf(songs: Collection<SongId>): Map<SongId, SongArtists>
}
