package dev.tomasflopes.musicplayer.core.model

/** A main artist (the artist tag). Featured artists never create an entry of their own. */
data class Artist(
    val id: ArtistId,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)
