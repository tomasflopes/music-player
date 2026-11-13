package dev.tomasflopes.musicplayer.fakes

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId

/** A song with harmless defaults; a test sets only what it looks at. */
fun testSong(
    id: Long,
    albumId: Long? = 1L,
    hasCover: Boolean = false,
    title: String = "Song $id",
    artistId: Long = 1L,
    artistName: String = "Artist",
    albumTitle: String? = albumId?.let { "Album $it" },
    dateAddedEpochSec: Long = 0,
    trackNumber: Int? = null,
    discNumber: Int? = null,
) = Song(
    id = SongId(id),
    title = title,
    artistId = ArtistId(artistId),
    artistName = artistName,
    albumId = albumId?.let(::AlbumId),
    albumTitle = albumTitle,
    albumArtist = "Artist",
    trackNumber = trackNumber,
    discNumber = discNumber,
    durationMs = 1_000,
    volumeName = "external_primary",
    path = "Music/Song $id.mp3",
    fileName = "Song $id.mp3",
    folderPath = "Music",
    dateAddedEpochSec = dateAddedEpochSec,
    contentUri = "content://media/external/audio/media/$id",
    hasCover = hasCover,
)

fun testArtist(
    id: Long,
    name: String,
    songCount: Int = 1,
) = Artist(ArtistId(id), name, songCount, albumCount = 1)

fun testAlbum(
    id: Long,
    title: String,
    albumArtist: String,
    coverSongId: Long? = null,
) = Album(AlbumId(id), title, albumArtist, songCount = 1, coverSongId = coverSongId?.let(::SongId))
