package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.SongId

/**
 * The artwork URI the mapper puts on a song's item and [CoverBitmapLoader] resolves: `musicplayer://cover/{songId}`.
 * It is not a real address, only a name for "the cover embedded in this song's file", kept as plain strings so the
 * mapping stays testable without Android.
 */
internal object CoverUri {
    private const val PREFIX = "musicplayer://cover/"

    fun of(songId: SongId): String = "$PREFIX${songId.value}"

    /** The song a cover URI names, or `null` for any other URI. */
    fun songIdOf(uri: String): SongId? {
        if (!uri.startsWith(PREFIX)) return null
        val id = uri.removePrefix(PREFIX)
        return id.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()?.let(::SongId)
    }
}
