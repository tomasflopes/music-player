package dev.tomasflopes.musicplayer.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tomasflopes.musicplayer.data.db.entity.AlbumEntity
import dev.tomasflopes.musicplayer.data.db.entity.ArtistEntity
import dev.tomasflopes.musicplayer.data.db.entity.SongEntity

internal fun inMemoryDatabase(): MusicDatabase =
    Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MusicDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal suspend fun MusicDatabase.artist(name: String): Long =
    artistDao().insert(ArtistEntity(name = name, sortName = name))

internal suspend fun MusicDatabase.album(
    title: String,
    albumArtist: String,
): Long = albumDao().insert(AlbumEntity(title = title, albumArtist = albumArtist, sortTitle = title))

internal fun songEntity(
    fileName: String,
    artistId: Long,
    albumId: Long? = null,
    disc: Int? = null,
    track: Int? = null,
    dateAdded: Long = 0,
    inLibrary: Boolean = true,
    folderPath: String = "Music/Album",
) = SongEntity(
    mediaStoreId = fileName.hashCode().toLong(),
    volumeName = "external_primary",
    path = "$folderPath/$fileName",
    fileName = fileName,
    folderPath = folderPath,
    title = fileName.substringBeforeLast('.'),
    sortTitle = fileName.substringBeforeLast('.').lowercase(),
    artistId = artistId,
    albumId = albumId,
    albumArtist = null,
    trackNumber = track,
    discNumber = disc,
    durationMs = 1_000,
    fileSize = 100,
    fileModified = 0,
    dateAdded = dateAdded,
    hasCover = false,
    tagVersion = "ID3V2_4",
    inLibrary = inLibrary,
)
