package dev.tomasflopes.musicplayer.core.search

import java.text.Normalizer
import java.util.Locale

private val ACCENTS = Regex("\\p{Mn}+")

/** Lower-case, accents removed, whitespace collapsed. Query and indexed text go through this. */
object Normalizer {
    fun normalize(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(ACCENTS, "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

    /** The individual words of [text], in order. */
    fun words(text: String): List<String> = normalize(text).split(" ").filter { it.isNotEmpty() }
}
