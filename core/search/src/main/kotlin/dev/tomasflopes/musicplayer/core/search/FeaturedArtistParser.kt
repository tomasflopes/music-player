package dev.tomasflopes.musicplayer.core.search

/**
 * Parses featured artists out of a song title, e.g. `Artist - Song feat. Ed Kendrick Lamar`.
 *
 * The marker is `feat.`, `feat`, `ft.` or `ft` as a whole word (case-insensitive). Everything after it is
 * matched against the library's known main-artist names, longest match first, so multi-word names like
 * `Kendrick Lamar` stay together. Anything that matches no known artist is kept as raw text, searchable but
 * never promoted to a real artist entry.
 */
object FeaturedArtistParser {
    private val MARKER = Regex("""(?i)\b(?:feat|ft)\.?\s+""")

    /** [knownArtists] canonical main-artist names, longest first. */
    fun parse(
        title: String,
        knownArtists: List<String>,
    ): List<String> {
        val tail = MARKER.find(title)?.let { title.substring(it.range.endInclusive + 1) } ?: return emptyList()
        return if (tail.isBlank()) emptyList() else parseTail(tail, knownArtists)
    }

    private fun parseTail(
        tail: String,
        knownArtists: List<String>,
    ): List<String> {
        val words = tail.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val known = knownArtists.map { it to it.split(" ").size }.sortedByDescending { it.second }

        val result = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            var matched = false
            for ((name, phraseWords) in known) {
                if (i + phraseWords <= words.size) {
                    val candidate = words.subList(i, i + phraseWords).joinToString(" ")
                    if (candidate.equals(name, ignoreCase = true)) {
                        result.add(name)
                        i += phraseWords
                        matched = true
                        break
                    }
                }
            }
            if (!matched) {
                result.add(words[i])
                i++
            }
        }
        return result
    }

    /** The marker position, so callers can show the title with the feature part split off. */
    fun markerIndex(title: String): Int? = MARKER.find(title)?.range?.start
}
