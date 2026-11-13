package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource

/** "Play these songs": [picked] is true when the owner tapped this very song (it feeds the log's started_by). */
data class PlayRequest(
    val songs: List<SongRef>,
    val startIndex: Int,
    val shuffle: Boolean,
    val source: PlaySource,
    val picked: Boolean,
)
