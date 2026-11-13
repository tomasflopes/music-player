package dev.tomasflopes.musicplayer.playback

/** Every tunable number about playback, in one place. */
data class PlaybackConfig(
    /** How many queue entries each side of the current song the player holds (plan C3). */
    val windowAhead: Int = 20,
    val windowBehind: Int = 20,
    /** How often the "pending play" checkpoint is written while a song plays (plan D6). */
    val pendingCheckpointMs: Long = 10_000,
    val queueSaveDebounceMs: Long = 1_000,
    /** Previous restarts the song when it has played longer than this, and goes back only before it. */
    val restartThresholdMs: Long = 3_000,
    /** How often the position shown on screen refreshes while playing. */
    val positionTickMs: Long = 500,
    /** The side of the cover the media session asks for: big enough for the lock screen and a car's display. */
    val notificationCoverPx: Int = 512,
)
