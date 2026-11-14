package dev.tomasflopes.musicplayer.core.model.playlists

/** The one rule for what a playlist may be called. */
object PlaylistName {
    const val MAX_LENGTH = 80

    /** [raw] trimmed and cut to [MAX_LENGTH], or `null` when nothing is left. */
    fun clean(raw: String): String? =
        raw
            .trim()
            .take(MAX_LENGTH)
            .trimEnd()
            .ifEmpty { null }
}
