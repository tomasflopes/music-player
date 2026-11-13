package dev.tomasflopes.musicplayer.playback

/** Every tunable number about playback, in one place. */
data class PlaybackConfig(
    /** How many queue entries each side of the current song the player holds (plan C3). */
    val windowAhead: Int = 20,
    val windowBehind: Int = 20,
    /** How often the "pending play" checkpoint is written while a song plays (plan D6). */
    val pendingCheckpointMs: Long = 10_000,
    val queueSaveDebounceMs: Long = 1_000,
)
