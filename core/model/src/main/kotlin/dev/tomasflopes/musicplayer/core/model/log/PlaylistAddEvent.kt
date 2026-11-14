package dev.tomasflopes.musicplayer.core.model.log

import dev.tomasflopes.musicplayer.core.model.SongId

/**
 * A song the owner added to a playlist (design §7.4: +3 on the song). Insert-only, like [PlayEvent]: removing the
 * song or deleting the playlist later does not take the signal back, so scores stay recomputable from the logs.
 */
data class PlaylistAddEvent(
    val songId: SongId,
    val playlistId: Long,
    val atEpochMs: Long,
)
