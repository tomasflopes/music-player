package dev.tomasflopes.musicplayer.playback.queue

/**
 * Where the saved queue lives (ADR 0036). Pure Kotlin port, so what `check` covers never needs Room or a device.
 * [load] never throws: a missing, damaged or older state is simply `null`.
 */
interface QueueStore {
    fun load(): SavedQueue?

    /** Replaces the saved state; `null` clears it. */
    fun save(saved: SavedQueue?)
}
