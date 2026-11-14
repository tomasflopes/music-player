package dev.tomasflopes.musicplayer.core.model

/** The artists of one song: the main artist (the tag, ADR 0026) and the featured artists that are main artists too. */
data class SongArtists(
    val main: ArtistId,
    val featured: List<ArtistId> = emptyList(),
)
