package dev.tomasflopes.musicplayer.core.model

/** A song with only what ordering and grouping look at; every other field gets a harmless default. */
internal fun song(
    fileName: String,
    disc: Int? = null,
    track: Int? = null,
    path: String = "Music/Album/$fileName",
    id: Long = path.hashCode().toLong(),
) = Song(
    id = SongId(id),
    title = fileName,
    artistId = ArtistId(1),
    artistName = "Artist",
    albumId = AlbumId(1),
    albumTitle = "Album",
    albumArtist = "Artist",
    trackNumber = track,
    discNumber = disc,
    durationMs = 1_000,
    volumeName = "external_primary",
    path = path,
    fileName = fileName,
    folderPath = path.substringBeforeLast('/'),
    dateAddedEpochSec = 0,
    contentUri = "content://media/external/audio/media/$id",
    hasCover = false,
)
