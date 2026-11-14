package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource

/**
 * The whole queue from the current song on, for "Save as playlist" (ADR 0044). [sources] are the sources of the
 * songs that were not queued by hand, so the save can refuse a queue that began as Shuffle all.
 */
data class QueueContent(
    val songs: List<SongId>,
    val sources: Set<PlaySource>,
)
