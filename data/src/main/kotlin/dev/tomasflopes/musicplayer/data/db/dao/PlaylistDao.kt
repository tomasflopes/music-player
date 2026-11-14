package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.PlaylistEntity
import dev.tomasflopes.musicplayer.data.db.entity.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

/** A playlist with the number of songs the owner can see (retired songs excluded) and the first one, for a cover. */
data class PlaylistRow(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "song_count") val songCount: Int,
    @ColumnInfo(name = "cover_song_id") val coverSongId: Long?,
)

/** A visible song's place in a playlist: what a reorder rewrites. */
data class PlaylistSlot(
    @ColumnInfo(name = "song_id") val songId: Long,
    val position: Int,
)

private const val VISIBLE = "JOIN song ON song.id = playlist_song.song_id AND song.in_library = 1"

private const val SELECT_PLAYLISTS =
    "SELECT playlist.*, " +
        "(SELECT COUNT(*) FROM playlist_song $VISIBLE WHERE playlist_song.playlist_id = playlist.id) AS song_count, " +
        "(SELECT playlist_song.song_id FROM playlist_song $VISIBLE WHERE playlist_song.playlist_id = playlist.id " +
        "ORDER BY playlist_song.position LIMIT 1) AS cover_song_id FROM playlist"

/** Playlists and their songs. Every song reader shows only songs in the library (`in_library = 1`, ADR 0021). */
@Dao
interface PlaylistDao {
    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlist SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(
        id: Long,
        name: String,
        now: Long,
    )

    /** The songs go with it (cascade). The listening log keeps its `playlist_id` values. */
    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE playlist SET last_used_at = :now, updated_at = :now WHERE id = :id")
    suspend fun touch(
        id: Long,
        now: Long,
    )

    @Query("$SELECT_PLAYLISTS ORDER BY playlist.last_used_at DESC, playlist.id DESC")
    fun observeAll(): Flow<List<PlaylistRow>>

    @Query("$SELECT_PLAYLISTS WHERE playlist.id = :id")
    fun observe(id: Long): Flow<PlaylistRow?>

    @Query(
        "SELECT song.*, artist.name AS artist_name, album.title AS album_title FROM playlist_song " +
            "JOIN song ON song.id = playlist_song.song_id AND song.in_library = 1 " +
            "JOIN artist ON artist.id = song.artist_id LEFT JOIN album ON album.id = song.album_id " +
            "WHERE playlist_song.playlist_id = :id ORDER BY playlist_song.position",
    )
    fun observeSongs(id: Long): Flow<List<SongRow>>

    /** Visible songs in play order, as ids with their places. */
    @Query(
        "SELECT playlist_song.song_id AS song_id, playlist_song.position AS position FROM playlist_song $VISIBLE " +
            "WHERE playlist_song.playlist_id = :id ORDER BY playlist_song.position",
    )
    suspend fun visibleSlots(id: Long): List<PlaylistSlot>

    /** What a play request carries: visible songs in play order. */
    @Query(
        "SELECT song.id AS id, song.artist_id AS artist_id FROM playlist_song $VISIBLE " +
            "WHERE playlist_song.playlist_id = :id ORDER BY playlist_song.position",
    )
    suspend fun visibleRefs(id: Long): List<SongRefRow>

    /** Every row, retired songs included: a new song goes after all of them. */
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_song WHERE playlist_id = :id")
    suspend fun lastPosition(id: Long): Int

    /** A song already in the playlist is left alone (primary key), so adding is safe to repeat. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(rows: List<PlaylistSongEntity>): List<Long>

    @Query("DELETE FROM playlist_song WHERE playlist_id = :id AND song_id = :songId")
    suspend fun removeSong(
        id: Long,
        songId: Long,
    )

    @Query("UPDATE playlist_song SET position = :position WHERE playlist_id = :id AND song_id = :songId")
    suspend fun setPosition(
        id: Long,
        songId: Long,
        position: Int,
    )
}
