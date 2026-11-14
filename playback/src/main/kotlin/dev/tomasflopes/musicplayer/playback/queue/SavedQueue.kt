package dev.tomasflopes.musicplayer.playback.queue

/** What survives the process: the whole queue and where the current song had got to (ADR 0036). */
data class SavedQueue(
    val snapshot: QueueSnapshot,
    val positionMs: Long,
)
