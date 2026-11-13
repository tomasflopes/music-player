package dev.tomasflopes.musicplayer.fakes

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId

/** A song with harmless defaults; a test sets only what it looks at. */
fun testSong(
    id: Long,
    albumId: Long? = 1L,
    hasCover: Boolean = false,
) = Song(
    id = SongId(id),
    title = "Song $id",
    artistId = ArtistId(1),
    artistName = "Artist",
    albumId = albumId?.let(::AlbumId),
    albumTitle = albumId?.let { "Album $it" },
    albumArtist = "Artist",
    trackNumber = null,
    discNumber = null,
    durationMs = 1_000,
    volumeName = "external_primary",
    path = "Music/Song $id.mp3",
    fileName = "Song $id.mp3",
    folderPath = "Music",
    dateAddedEpochSec = 0,
    contentUri = "content://media/external/audio/media/$id",
    hasCover = hasCover,
)
