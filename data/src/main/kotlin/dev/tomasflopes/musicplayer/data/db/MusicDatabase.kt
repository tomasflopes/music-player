package dev.tomasflopes.musicplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.tomasflopes.musicplayer.data.db.dao.AlbumDao
import dev.tomasflopes.musicplayer.data.db.dao.ArtistDao
import dev.tomasflopes.musicplayer.data.db.dao.ArtistImageDao
import dev.tomasflopes.musicplayer.data.db.dao.FolderDao
import dev.tomasflopes.musicplayer.data.db.dao.PlayEventDao
import dev.tomasflopes.musicplayer.data.db.dao.PlaylistDao
import dev.tomasflopes.musicplayer.data.db.dao.SearchEventDao
import dev.tomasflopes.musicplayer.data.db.dao.SongDao
import dev.tomasflopes.musicplayer.data.db.dao.SongFeatureDao
import dev.tomasflopes.musicplayer.data.db.dao.SongScanDao
import dev.tomasflopes.musicplayer.data.db.entity.AlbumEntity
import dev.tomasflopes.musicplayer.data.db.entity.ArtistEntity
import dev.tomasflopes.musicplayer.data.db.entity.ArtistImageLookupEntity
import dev.tomasflopes.musicplayer.data.db.entity.ArtistImageOverrideEntity
import dev.tomasflopes.musicplayer.data.db.entity.FolderEntity
import dev.tomasflopes.musicplayer.data.db.entity.PlayEventEntity
import dev.tomasflopes.musicplayer.data.db.entity.PlaylistEntity
import dev.tomasflopes.musicplayer.data.db.entity.PlaylistSongEntity
import dev.tomasflopes.musicplayer.data.db.entity.SearchEventEntity
import dev.tomasflopes.musicplayer.data.db.entity.SongEntity
import dev.tomasflopes.musicplayer.data.db.entity.SongFeatureEntity

@Database(
    version = 3,
    exportSchema = true,
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        FolderEntity::class,
        SongFeatureEntity::class,
        PlayEventEntity::class,
        SearchEventEntity::class,
        ArtistImageOverrideEntity::class,
        ArtistImageLookupEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    abstract fun songScanDao(): SongScanDao

    abstract fun artistDao(): ArtistDao

    abstract fun albumDao(): AlbumDao

    abstract fun folderDao(): FolderDao

    abstract fun songFeatureDao(): SongFeatureDao

    abstract fun playEventDao(): PlayEventDao

    abstract fun searchEventDao(): SearchEventDao

    abstract fun artistImageDao(): ArtistImageDao

    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val NAME = "music.db"
    }
}
