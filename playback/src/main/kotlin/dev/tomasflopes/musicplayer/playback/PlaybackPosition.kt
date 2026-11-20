package dev.tomasflopes.musicplayer.playback

/** Where the current song is and how long it is, in milliseconds. Kept out of [PlaybackState] (ADR 0077). */
data class PlaybackPosition(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)
