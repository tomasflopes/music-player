package dev.tomasflopes.musicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tomasflopes.musicplayer.data.db.entity.AlbumEntity
import dev.tomasflopes.musicplayer.data.db.entity.ArtistEntity
import dev.tomasflopes.musicplayer.data.db.entity.FolderEntity
import dev.tomasflopes.musicplayer.data.db.entity.SongFeatureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Insert
    suspend fun insert(artist: ArtistEntity): Long

    @Query("SELECT * FROM artist WHERE name = :name")
    suspend fun byName(name: String): ArtistEntity?
}

@Dao
interface AlbumDao {
    @Insert
    suspend fun insert(album: AlbumEntity): Long

    @Query("SELECT * FROM album WHERE title = :title AND album_artist = :albumArtist")
    suspend fun byKey(
        title: String,
        albumArtist: String,
    ): AlbumEntity?
}

@Dao
interface FolderDao {
    @Insert
    suspend fun insert(folder: FolderEntity)

    @Query("SELECT * FROM folder WHERE parent_path IS :parentPath ORDER BY name")
    fun observeChildren(parentPath: String?): Flow<List<FolderEntity>>
}

@Dao
interface SongFeatureDao {
    @Insert
    suspend fun insert(feature: SongFeatureEntity): Long
}
