package dev.tomasflopes.musicplayer.core.search

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val MARKS = Regex("\\p{Mn}+")
private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
private val NON_ALPHANUMERIC_OR_SPACE = Regex("[^\\p{L}\\p{N} ]+")
private val WHITESPACE = Regex("\\s+")

/** More folded strings than a library plus its queries ever needs; past it the cache starts over. */
private const val MAX_CACHED_VARIANTS = 100_000

/**
 * Matching-only text folding. Display text stays exactly as tagged; these forms are never shown.
 *
 * [spaced] keeps words apart and turns punctuation into spaces, while [compact] removes punctuation
 * without inserting a space, so `JAY-Z` can be found by `jay z` and by `jayz`.
 */
object TextNormalizer {
    // Folding is pure and the matcher asks for the same library strings on every keystroke, so each is folded once.
    private val variantCache = ConcurrentHashMap<String, Set<String>>()

    /** Lower-case, accents stripped, every run of non letter/digit replaced by one space. */
    fun spaced(text: String): String =
        base(text)
            .replace(NON_ALPHANUMERIC, " ")
            .replace(WHITESPACE, " ")
            .trim()

    /** Lower-case, accents stripped, punctuation removed, spaces kept and collapsed. */
    fun compact(text: String): String =
        base(text)
            .replace(NON_ALPHANUMERIC_OR_SPACE, "")
            .replace(WHITESPACE, " ")
            .trim()

    /**
     * Every folded form that should match [text]. `$` is also read as `s`, so `A$AP Rocky` is found by
     * `asap rocky` as well as by `a$ap` and `aap rocky`.
     */
    fun variants(text: String): Set<String> {
        if (variantCache.size >= MAX_CACHED_VARIANTS) variantCache.clear()
        return variantCache.getOrPut(text) { foldVariants(text) }
    }

    private fun foldVariants(text: String): Set<String> {
        val result = linkedSetOf<String>()
        result += spaced(text)
        result += compact(text)
        if (text.contains('$')) result += compact(text.replace('$', 's'))
        result.remove("")
        return result.toSet()
    }

    /** The individual words of an already folded string, in order. */
    fun tokens(normalized: String): List<String> = normalized.split(' ').filter { it.isNotEmpty() }

    private fun base(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase(Locale.ROOT)
}
