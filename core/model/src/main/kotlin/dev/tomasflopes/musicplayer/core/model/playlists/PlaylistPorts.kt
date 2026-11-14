package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import kotlinx.coroutines.flow.Flow

/** Reads playlists. Songs that are no longer in the library are left out everywhere (ADR 0021, 0037). */
interface PlaylistReader {
    /** Most recently used first. */
    fun observePlaylists(): Flow<List<Playlist>>

    /** `null` once the playlist is deleted. */
    fun observePlaylist(id: PlaylistId): Flow<Playlist?>

    /** In playlist order. */
    fun observeSongs(id: PlaylistId): Flow<List<Song>>

    /** What a play request carries, in playlist order. */
    suspend fun songRefs(id: PlaylistId): List<SongRef>
}

/** Changes playlists. Times are passed in so the implementation holds no clock. */
interface PlaylistStore {
    suspend fun create(
        name: String,
        nowEpochMs: Long,
    ): PlaylistId

    suspend fun rename(
        id: PlaylistId,
        name: String,
        nowEpochMs: Long,
    )

    /** Its songs go with it. The listening log keeps the id. */
    suspend fun delete(id: PlaylistId)

    /**
     * Appends [songs] in order after everything already there. A song that is already in the playlist is left where
     * it is. Counts as using the playlist even when nothing new went in. Returns how many songs were added.
     */
    suspend fun addSongs(
        id: PlaylistId,
        songs: List<SongId>,
        nowEpochMs: Long,
    ): Int

    suspend fun removeSong(
        id: PlaylistId,
        songId: SongId,
    )

    /**
     * Puts the visible songs in [order]. Ignored unless [order] holds exactly the songs now visible, so a stale
     * screen cannot lose or invent songs. Rows of retired songs keep their places (see [PlaylistOrder]).
     */
    suspend fun reorder(
        id: PlaylistId,
        order: List<SongId>,
    )
}
