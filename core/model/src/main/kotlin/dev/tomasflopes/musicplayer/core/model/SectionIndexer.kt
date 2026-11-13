package dev.tomasflopes.musicplayer.core.model

import java.text.Normalizer

/** One letter of the A–Z fast-scroller and the first list position it jumps to. */
data class Section(
    val label: Char,
    val firstIndex: Int,
)

/** A–Z sections over an already sorted list. Accented letters go under their base letter; anything else is `#`. */
object SectionIndexer {
    const val OTHER = '#'
    private val ACCENTS = Regex("\\p{Mn}+")

    fun sections(sortKeys: List<String>): List<Section> {
        val seen = mutableSetOf<Char>()
        return sortKeys.mapIndexedNotNull { index, key ->
            val label = label(key)
            if (seen.add(label)) Section(label, index) else null
        }
    }

    private fun label(key: String): Char {
        val first = key.firstOrNull() ?: return OTHER
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).replace(ACCENTS, "").uppercase()
        return base.singleOrNull()?.takeIf { it in 'A'..'Z' } ?: OTHER
    }
}
