package dev.tomasflopes.musicplayer.tools.analyser

import java.text.Normalizer
import java.util.Locale

/** Finds the seed song of `analyser similar` by a part of its path (ADR 0073). */
object SeedMatcher {
    private const val LISTED = 20

    /**
     * The index of the one path that contains [seed], ignoring case and the way accents are encoded. Throws
     * [UsageException] for no match, and for several, which it lists so the owner can use a longer part.
     */
    fun find(
        paths: List<String>,
        seed: String,
    ): Int {
        val needle = fold(seed)
        val hits = paths.indices.filter { needle in fold(paths[it]) }
        return when (hits.size) {
            1 -> hits.first()
            0 -> throw UsageException("No song path contains \"$seed\"")
            else -> throw UsageException(tooMany(paths, hits, seed))
        }
    }

    private fun tooMany(
        paths: List<String>,
        hits: List<Int>,
        seed: String,
    ): String =
        buildString {
            append("\"$seed\" matches ${hits.size} songs, use a longer part of the path:")
            hits.take(LISTED).forEach { append("\n  ").append(paths[it]) }
            if (hits.size > LISTED) append("\n  and ${hits.size - LISTED} more")
        }

    private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)
}
