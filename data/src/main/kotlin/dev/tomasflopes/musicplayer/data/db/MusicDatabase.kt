package dev.tomasflopes.musicplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.tomasflopes.musicplayer.data.db.dao.AlbumDao
import dev.tomasflopes.musicplayer.data.db.dao.ArtistDao
import dev.tomasflopes.musicplayer.data.db.dao.FolderDao
import dev.tomasflopes.musicplayer.data.db.dao.PlayEventDao
import dev.tomasflopes.musicplayer.data.db.dao.SearchEventDao
import dev.tomasflopes.musicplayer.data.db.dao.SongDao
import dev.tomasflopes.musicplayer.data.db.dao.SongFeatureDao
import dev.tomasflopes.musicplayer.data.db.entity.AlbumEntity
import dev.tomasflopes.musicplayer.data.db.entity.ArtistEntity
import dev.tomasflopes.musicplayer.data.db.entity.FolderEntity
import dev.tomasflopes.musicplayer.data.db.entity.PlayEventEntity
import dev.tomasflopes.musicplayer.data.db.entity.SearchEventEntity
import dev.tomasflopes.musicplayer.data.db.entity.SongEntity
import dev.tomasflopes.musicplayer.data.db.entity.SongFeatureEntity

@Database(
    version = 1,
    exportSchema = true,
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        FolderEntity::class,
        SongFeatureEntity::class,
        PlayEventEntity::class,
        SearchEventEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    abstract fun artistDao(): ArtistDao

    abstract fun albumDao(): AlbumDao

    abstract fun folderDao(): FolderDao

    abstract fun songFeatureDao(): SongFeatureDao

    abstract fun playEventDao(): PlayEventDao

    abstract fun searchEventDao(): SearchEventDao

    companion object {
        const val NAME = "music.db"
    }
}
