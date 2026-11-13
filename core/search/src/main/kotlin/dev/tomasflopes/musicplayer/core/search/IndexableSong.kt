package dev.tomasflopes.musicplayer.core.search

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId

/** A song reduced to the fields the search index covers. Pure Kotlin, no Android types. */
data class IndexableSong(
    val id: SongId,
    val artistId: ArtistId,
    val title: String,
    val artist: String,
    val albumId: AlbumId?,
    val album: String?,
    val albumArtist: String?,
    val folderName: String?,
    /** Featured artist names parsed from the title, canonical (known artist) or raw. */
    val featured: List<String>,
)
