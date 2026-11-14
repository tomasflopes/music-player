package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.tomasflopes.musicplayer.data.db.entity.ArtistImageLookupEntity
import dev.tomasflopes.musicplayer.data.db.entity.ArtistImageOverrideEntity

@Dao
interface ArtistImageDao {
    @Query("SELECT * FROM artist_image_override WHERE artist_id = :artistId")
    suspend fun override(artistId: Long): ArtistImageOverrideEntity?

    @Upsert
    suspend fun upsertOverride(entity: ArtistImageOverrideEntity)

    @Query("DELETE FROM artist_image_override WHERE artist_id = :artistId")
    suspend fun clearOverride(artistId: Long)

    @Query("SELECT * FROM artist_image_lookup WHERE artist_id = :artistId")
    suspend fun lookup(artistId: Long): ArtistImageLookupEntity?

    @Upsert
    suspend fun upsertLookup(entity: ArtistImageLookupEntity)
}
