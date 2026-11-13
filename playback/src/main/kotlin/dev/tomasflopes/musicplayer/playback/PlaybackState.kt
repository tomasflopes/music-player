package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode

data class PlaybackState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val upcoming: List<Song> = emptyList(),
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)
