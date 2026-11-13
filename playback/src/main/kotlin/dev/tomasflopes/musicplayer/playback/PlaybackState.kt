package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode

data class PlaybackState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val upcoming: List<Song> = emptyList(),
    /** How many of the first [upcoming] songs came from Play next / Add to queue (ADR 0010: they come first). */
    val manualUpcoming: Int = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
) {
    /** Whether the song at [index] of [upcoming] was queued by hand. */
    fun isManualUpcoming(index: Int): Boolean = index in 0 until manualUpcoming
}
