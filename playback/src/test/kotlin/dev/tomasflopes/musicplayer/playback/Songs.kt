package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId

internal fun song(
    id: Long,
    title: String = "Song $id",
    artist: String = "Artist",
    album: String? = "Album",
    hasCover: Boolean = false,
) = Song(
    id = SongId(id),
    title = title,
    artistId = ArtistId(1),
    artistName = artist,
    albumId = album?.let { AlbumId(1) },
    albumTitle = album,
    albumArtist = album?.let { artist },
    trackNumber = null,
    discNumber = null,
    durationMs = 180_000,
    volumeName = "external_primary",
    path = "Music/$id.mp3",
    fileName = "$id.mp3",
    folderPath = "Music",
    dateAddedEpochSec = 0,
    contentUri = "content://media/external_primary/audio/media/$id",
    hasCover = hasCover,
)
