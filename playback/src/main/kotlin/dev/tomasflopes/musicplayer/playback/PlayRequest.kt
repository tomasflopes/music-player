package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource

/**
 * "Play these songs": [picked] is true when the owner tapped this very song (it feeds the log's started_by). A
 * [shuffle] request that is not [picked] starts on a random song; [startIndex] only matters when it is.
 * [playlistId] is the playlist the songs come from, if any; it ends up in every log row of this play (ADR 0042).
 */
data class PlayRequest(
    val songs: List<SongRef>,
    val startIndex: Int,
    val shuffle: Boolean,
    val source: PlaySource,
    val picked: Boolean,
    val playlistId: Long? = null,
)
