package dev.tomasflopes.musicplayer.core.model

/**
 * A featured artist parsed from a song title. [artistId] is set when the name matches a main artist in the
 * library; otherwise only [rawName] is known.
 */
data class SongFeature(
    val songId: SongId,
    val artistId: ArtistId?,
    val rawName: String?,
)
