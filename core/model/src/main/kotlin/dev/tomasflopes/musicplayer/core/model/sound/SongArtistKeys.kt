package dev.tomasflopes.musicplayer.core.model.sound

import java.text.Normalizer
import java.util.Locale

/**
 * The artists of one song as comparable keys (see [key]): the [main] artist and the [featured] ones. The outlier guard
 * (ADR 0087) only compares keys, so the app can build them from song rows and the analyser from file names.
 */
class SongArtistKeys(
    val main: String,
    val featured: Set<String> = emptySet(),
) {
    /** True when [artistKey] is the main artist or one of the featured ones. */
    fun includes(artistKey: String): Boolean = main == artistKey || artistKey in featured

    companion object {
        /** [name] NFC-normalised, lower case and trimmed. */
        fun key(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC).trim().lowercase(Locale.ROOT)
    }
}
