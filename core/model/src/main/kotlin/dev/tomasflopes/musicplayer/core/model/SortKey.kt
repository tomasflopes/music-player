package dev.tomasflopes.musicplayer.core.model

import java.text.Normalizer
import java.util.Locale

/**
 * The text the library lists sort by, and the text the A-Z rail sections by. The scanner stores it (`sort_title`,
 * `sort_name`) and the UI derives it again from a title or name, so both always agree on where an item sits.
 */
object SortKey {
    private val ACCENTS = Regex("\\p{Mn}+")
    private val LEADING_ARTICLE = Regex("^(the|a|an) ", RegexOption.IGNORE_CASE)

    /** Lower case, accents removed, a leading article dropped (unless that is the whole text). */
    fun of(text: String): String {
        val plain =
            Normalizer
                .normalize(text, Normalizer.Form.NFD)
                .replace(ACCENTS, "")
                .lowercase(Locale.ROOT)
                .trim()
        return plain.replace(LEADING_ARTICLE, "").ifEmpty { plain }
    }
}
