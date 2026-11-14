package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.SongId
import kotlinx.coroutines.flow.Flow

/**
 * The suggestions the owner dismissed with "✕", per playlist (design 7.13, ADR 0051). A dismissal belongs to one
 * playlist: it never reaches the global taste score and goes away with the playlist.
 */
interface PlaylistDismissals {
    /** Songs dismissed for [id], retired ones included. Emits again whenever one is dismissed. */
    fun observe(id: PlaylistId): Flow<Set<SongId>>

    /** Dismisses [song] for [id]; repeating it changes nothing. Ignored when the playlist does not exist. */
    suspend fun dismiss(
        id: PlaylistId,
        song: SongId,
        nowEpochMs: Long,
    )
}
