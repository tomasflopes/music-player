package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.SongId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** [PlaylistDismissals] kept in memory: a set of song ids per playlist. */
class InMemoryDismissals : PlaylistDismissals {
    private val state = MutableStateFlow<Map<PlaylistId, Set<SongId>>>(emptyMap())

    override fun observe(id: PlaylistId): Flow<Set<SongId>> = state.map { it[id].orEmpty() }

    override suspend fun dismiss(
        id: PlaylistId,
        song: SongId,
        nowEpochMs: Long,
    ) = state.update { current -> current + (id to (current[id].orEmpty() + song)) }
}
