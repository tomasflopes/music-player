package dev.tomasflopes.musicplayer.core.model.log

import dev.tomasflopes.musicplayer.core.model.SongId

/** One row of the listening log. Insert-only: never updated, never deleted. */
data class PlayEvent(
    val songId: SongId,
    val startedAtEpochMs: Long,
    val listenedMs: Long,
    val songDurationMs: Long,
    val endedBy: EndedBy,
    val source: PlaySource,
    val startedBy: StartedBy,
    /** Whether shuffle was on while this song played (ADR 0008). */
    val shuffle: Boolean,
    val mixId: Long? = null,
    val playlistId: Long? = null,
)
