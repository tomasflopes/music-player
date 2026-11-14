package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId

/** One song's decayed global score at one moment, and when it was last played (the tie-break). */
data class SongScore(
    val songId: SongId,
    val score: Double,
    val lastPlayedAtEpochMs: Long,
)
