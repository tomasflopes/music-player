package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.SongId

/** Where a reorder writes (ADR 0038). Pure, so every store shares it. */
object PlaylistOrder {
    /**
     * [current] is each visible song with its stored position, in play order. The songs of [order] take, in that
     * order, the positions the visible songs held, so a hidden (retired) row between them never moves. `null` when
     * [order] is not the same songs as [current].
     */
    fun reassign(
        current: List<Pair<SongId, Int>>,
        order: List<SongId>,
    ): List<Pair<SongId, Int>>? = TODO("not yet")
}
