package dev.tomasflopes.musicplayer.data.repo

import androidx.room.withTransaction
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.playlists.Playlist
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistOrder
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistReader
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistStore
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.dao.PlaylistRow
import dev.tomasflopes.musicplayer.data.db.entity.PlaylistEntity
import dev.tomasflopes.musicplayer.data.db.entity.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val NOT_INSERTED = -1L

private fun PlaylistRow.toPlaylist() =
    Playlist(
        id = PlaylistId(playlist.id),
        name = playlist.name,
        songCount = songCount,
        coverSongId = coverSongId?.let(::SongId),
        lastUsedAtEpochMs = playlist.lastUsedAt,
    )

class RoomPlaylistReader(
    private val db: MusicDatabase,
) : PlaylistReader {
    override fun observePlaylists(): Flow<List<Playlist>> =
        db.playlistDao().observeAll().map { rows -> rows.map(PlaylistRow::toPlaylist) }

    override fun observePlaylist(id: PlaylistId): Flow<Playlist?> =
        db.playlistDao().observe(id.value).map { it?.toPlaylist() }

    override fun observeSongs(id: PlaylistId): Flow<List<Song>> =
        db.playlistDao().observeSongs(id.value).map { rows -> rows.map { it.toSong() } }

    override suspend fun songRefs(id: PlaylistId): List<SongRef> =
        db.playlistDao().visibleRefs(id.value).map { it.toSongRef() }
}

class RoomPlaylistStore(
    private val db: MusicDatabase,
) : PlaylistStore {
    private val dao get() = db.playlistDao()

    override suspend fun create(
        name: String,
        nowEpochMs: Long,
    ): PlaylistId =
        PlaylistId(
            dao.insert(
                PlaylistEntity(name = name, createdAt = nowEpochMs, updatedAt = nowEpochMs, lastUsedAt = nowEpochMs),
            ),
        )

    override suspend fun rename(
        id: PlaylistId,
        name: String,
        nowEpochMs: Long,
    ) = dao.rename(id.value, name, nowEpochMs)

    override suspend fun delete(id: PlaylistId) = dao.delete(id.value)

    override suspend fun addSongs(
        id: PlaylistId,
        songs: List<SongId>,
        nowEpochMs: Long,
    ): Int =
        db.withTransaction {
            val start = dao.lastPosition(id.value) + 1
            val rows =
                songs.distinct().mapIndexed { index, songId ->
                    PlaylistSongEntity(id.value, songId.value, start + index, nowEpochMs)
                }
            val added = dao.insertSongs(rows).count { it != NOT_INSERTED }
            dao.touch(id.value, nowEpochMs)
            added
        }

    override suspend fun removeSong(
        id: PlaylistId,
        songId: SongId,
    ) = dao.removeSong(id.value, songId.value)

    override suspend fun reorder(
        id: PlaylistId,
        order: List<SongId>,
    ) = db.withTransaction {
        val current = dao.visibleSlots(id.value).map { SongId(it.songId) to it.position }
        PlaylistOrder.reassign(current, order)?.forEach { (songId, position) ->
            dao.setPosition(id.value, songId.value, position)
        }
        Unit
    }
}
