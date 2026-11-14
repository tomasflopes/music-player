package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

data class SongMainArtistRow(
    val id: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
)

data class SongFeaturedArtistRow(
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
)

/** Which artists songs belong to: the tag artist, and the featured artists that are main artists too. */
@Dao
interface SongArtistDao {
    @Query("SELECT id, artist_id FROM song WHERE id IN (:ids)")
    suspend fun mainArtists(ids: List<Long>): List<SongMainArtistRow>

    @Query(
        "SELECT song_id, artist_id FROM song_feature WHERE artist_id IS NOT NULL AND song_id IN (:ids) " +
            "ORDER BY song_id, id",
    )
    suspend fun featuredArtists(ids: List<Long>): List<SongFeaturedArtistRow>
}
