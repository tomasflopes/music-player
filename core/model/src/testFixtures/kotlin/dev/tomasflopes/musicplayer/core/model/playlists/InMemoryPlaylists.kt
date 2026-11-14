package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.ref
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Playlists kept in memory, behaving like the Room ones: a song is in a playlist once, and a song that is not in
 * [library] is retired, so it is hidden but keeps its place. A test changes [library] to retire or revive songs.
 */
class InMemoryPlaylists(
    songs: List<Song> = emptyList(),
) : PlaylistReader,
    PlaylistStore {
    val library = MutableStateFlow(songs)

    private data class Row(
        val songId: SongId,
        val position: Int,
    )

    private data class Stored(
        val name: String,
        val lastUsed: Long,
        val rows: List<Row> = emptyList(),
    )

    private data class State(
        val nextId: Long = 1,
        val lists: Map<PlaylistId, Stored> = emptyMap(),
    )

    private val state = MutableStateFlow(State())

    /** Every stored song id in position order, retired ones included. */
    fun storedSongs(id: PlaylistId): List<SongId> =
        state.value.lists[id]
            ?.rows
            ?.sortedBy { it.position }
            ?.map { it.songId }
            .orEmpty()

    fun nameOf(id: PlaylistId): String? = state.value.lists[id]?.name

    fun lastUsedOf(id: PlaylistId): Long? = state.value.lists[id]?.lastUsed

    private fun visible(
        stored: Stored,
        library: List<Song>,
    ): List<Song> {
        val byId = library.associateBy { it.id }
        return stored.rows.sortedBy { it.position }.mapNotNull { byId[it.songId] }
    }

    override fun observePlaylists(): Flow<List<Playlist>> =
        combine(state, library) { current, songs ->
            current.lists
                .map { (id, stored) -> id to toPlaylist(id, stored, songs) }
                .sortedWith(
                    compareByDescending<Pair<PlaylistId, Playlist>> { it.second.lastUsedAtEpochMs }
                        .thenByDescending { it.first.value },
                ).map { it.second }
        }

    override fun observePlaylist(id: PlaylistId): Flow<Playlist?> =
        combine(state, library) { current, songs -> current.lists[id]?.let { toPlaylist(id, it, songs) } }

    override fun observeSongs(id: PlaylistId): Flow<List<Song>> =
        combine(state, library) { current, songs -> current.lists[id]?.let { visible(it, songs) }.orEmpty() }

    override suspend fun songRefs(id: PlaylistId): List<SongRef> =
        state.value.lists[id]
            ?.let { visible(it, library.value).map(Song::ref) }
            .orEmpty()

    private fun toPlaylist(
        id: PlaylistId,
        stored: Stored,
        songs: List<Song>,
    ): Playlist {
        val shown = visible(stored, songs)
        return Playlist(id, stored.name, shown.size, shown.firstOrNull()?.id, stored.lastUsed)
    }

    override suspend fun create(
        name: String,
        nowEpochMs: Long,
    ): PlaylistId {
        var created = PlaylistId(0)
        state.update { current ->
            created = PlaylistId(current.nextId)
            current.copy(nextId = current.nextId + 1, lists = current.lists + (created to Stored(name, nowEpochMs)))
        }
        return created
    }

    override suspend fun rename(
        id: PlaylistId,
        name: String,
        nowEpochMs: Long,
    ) = change(id) { it.copy(name = name) }

    override suspend fun delete(id: PlaylistId) = state.update { it.copy(lists = it.lists - id) }

    override suspend fun addSongs(
        id: PlaylistId,
        songs: List<SongId>,
        nowEpochMs: Long,
    ): Int {
        var added = 0
        change(id) { stored ->
            val present = stored.rows.map { it.songId }.toSet()
            val fresh = songs.filter { it !in present }.distinct()
            added = fresh.size
            val start = (stored.rows.maxOfOrNull { it.position } ?: -1) + 1
            stored.copy(
                lastUsed = nowEpochMs,
                rows = stored.rows + fresh.mapIndexed { index, songId -> Row(songId, start + index) },
            )
        }
        return added
    }

    override suspend fun removeSong(
        id: PlaylistId,
        songId: SongId,
    ) = change(id) { stored -> stored.copy(rows = stored.rows.filter { it.songId != songId }) }

    override suspend fun reorder(
        id: PlaylistId,
        order: List<SongId>,
    ) = change(id) { stored ->
        val shown = visible(stored, library.value).map { it.id }.toSet()
        val current = stored.rows.sortedBy { it.position }.filter { it.songId in shown }
        val moves =
            PlaylistOrder.reassign(current.map { it.songId to it.position }, order)?.toMap() ?: return@change stored
        stored.copy(rows = stored.rows.map { row -> moves[row.songId]?.let { row.copy(position = it) } ?: row })
    }

    private fun change(
        id: PlaylistId,
        transform: (Stored) -> Stored,
    ) = state.update { current ->
        val stored = current.lists[id] ?: return@update current
        current.copy(lists = current.lists + (id to transform(stored)))
    }
}
