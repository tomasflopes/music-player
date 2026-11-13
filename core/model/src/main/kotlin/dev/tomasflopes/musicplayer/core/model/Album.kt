package dev.tomasflopes.musicplayer.core.model

/** One [AlbumKey]: an album title together with its album artist. */
data class Album(
    val id: AlbumId,
    val title: String,
    val albumArtist: String,
    val songCount: Int,
    val coverSongId: SongId?,
)
