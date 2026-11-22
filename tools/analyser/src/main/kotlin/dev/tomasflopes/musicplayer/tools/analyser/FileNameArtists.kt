package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.model.sound.SongArtistKeys

/**
 * The artists of a song from its file name alone (ADR 0087; the analyser has paths, not song rows): `Artist - Title`
 * names the main artist and `feat.` or `ft.` in the title starts the featured ones, as `Artist - Title feat. A, B`.
 * A featured part with no commas (`feat. Joint One Botje`) is cut with the artists the library knows ([known], as
 * [SongArtistKeys.key]s), longest name first; words that match none are kept together as one name.
 */
object FileNameArtists {
    private const val DASH = " - "
    private const val MP3 = ".mp3"
    private val MARKER = Regex("""(?i)\b(?:feat|ft)\.?\s+""")
    private val LIST_SEPARATOR = Regex("""\s*[,&]\s*""")
    private val WORD_SEPARATOR = Regex("""\s+""")

    /** The main artist of [pathOrName] as a key, or null when its file name has no `Artist - Title` shape. */
    fun main(pathOrName: String): String? = parse(pathOrName)?.main

    fun parse(
        pathOrName: String,
        known: Set<String> = emptySet(),
    ): SongArtistKeys? {
        val file = pathOrName.substringAfterLast('/')
        val name = if (file.endsWith(MP3, ignoreCase = true)) file.dropLast(MP3.length) else file
        val dash = name.indexOf(DASH)
        if (dash <= 0) return null
        val title = name.substring(dash + DASH.length)
        val tail = MARKER.find(title)?.let { title.substring(it.range.last + 1).trimEnd(')', ']', ' ') }
        val featured = if (tail == null) emptySet() else featured(tail, known)
        return SongArtistKeys(SongArtistKeys.key(name.substring(0, dash)), featured)
    }

    private fun featured(
        tail: String,
        known: Set<String>,
    ): Set<String> =
        tail
            .split(LIST_SEPARATOR)
            .flatMap { chunk -> names(chunk.trim().split(WORD_SEPARATOR).filter { it.isNotEmpty() }, known) }
            .filter { it.isNotEmpty() }
            .toSet()

    /** Cuts [words] into names: a run of words that is a known artist (longest first), else unknown words together. */
    private fun names(
        words: List<String>,
        known: Set<String>,
    ): List<String> {
        val result = ArrayList<String>()
        val unknown = ArrayList<String>()
        var i = 0
        while (i < words.size) {
            val length = (words.size - i downTo 1).firstOrNull { keyOf(words.subList(i, i + it)) in known }
            if (length == null) {
                unknown += words[i]
                i++
            } else {
                if (unknown.isNotEmpty()) {
                    result += keyOf(unknown)
                    unknown.clear()
                }
                result += keyOf(words.subList(i, i + length))
                i += length
            }
        }
        if (unknown.isNotEmpty()) result += keyOf(unknown)
        return result
    }

    private fun keyOf(words: List<String>): String = SongArtistKeys.key(words.joinToString(" "))
}
