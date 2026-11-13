package dev.tomasflopes.musicplayer.core.model

/**
 * What makes two songs the same album: the album title and the album artist, compared exactly as tagged.
 * The same title by two different album artists is two albums.
 */
data class AlbumKey(
    val title: String,
    val albumArtist: String,
) {
    companion object {
        /** `null` when the song has no album. A missing album artist falls back to the artist. */
        fun of(
            album: String?,
            albumArtist: String?,
            artist: String?,
        ): AlbumKey? {
            val title = album?.takeIf { it.isNotBlank() } ?: return null
            val owner =
                albumArtist?.takeIf { it.isNotBlank() }
                    ?: artist?.takeIf { it.isNotBlank() }
                    ?: DisplayFallbacks.UNKNOWN_ARTIST
            return AlbumKey(title, owner)
        }
    }
}
