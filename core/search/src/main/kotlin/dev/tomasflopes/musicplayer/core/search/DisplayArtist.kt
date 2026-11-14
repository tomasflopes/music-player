package dev.tomasflopes.musicplayer.core.search

private val EXTENSION = Regex("""\.[A-Za-z0-9]{1,4}$""")
private val TRACK_PREFIX = Regex("""^(?:\d{1,3}\s*[.)_]\s*|\d{1,3}\s*-\s+(?=.*\s[-–—]\s)|0\d\s+)""")
private val TITLE_SEPARATOR = Regex("""\s[-–—]\s""")
private val FEATURING_TAIL = Regex("""(?i)\s+(?:feat|ft)\.?\s.*$""")
private val CREDIT_SPLIT = Regex("""(?i)\s*(?:[,;/&+]|\band\b|\bx\b|\b(?:feat|ft)\b\.?)\s*""")

/**
 * The artist a song shows. Some files carry a playlist-like artist tag (`Guitarra`) while the file is named
 * `Rui Veloso - Anel de Rubi.mp3`. When the artist parsed from the file name does not match the tag, the file-name
 * artist is shown. Display only: grouping, artist pages and shuffles keep using the tag artist.
 */
object DisplayArtist {
    /** [tagArtist] unless [fileName] names a different artist (see [fromFileName] and [matches]). */
    fun of(
        tagArtist: String,
        fileName: String,
    ): String {
        val fromFile = fromFileName(fileName) ?: return tagArtist
        return if (matches(tagArtist, fromFile)) tagArtist else fromFile
    }

    /**
     * The `Artist` of an `Artist - Title` file name, or null when there is none. Drops the extension, a leading
     * track number (`01 `, `01.`, `01 - `, but never `21 Savage`) and a trailing `feat.` credit. Only a spaced
     * hyphen or dash separates artist from title, so `Jay-Z` is not split.
     */
    fun fromFileName(fileName: String): String? {
        val stem =
            fileName
                .replace(EXTENSION, "")
                .replace(TRACK_PREFIX, "")
                .trim()
        val artist = TITLE_SEPARATOR.find(stem)?.let { stem.substring(0, it.range.first) } ?: return null
        return artist
            .replace(FEATURING_TAIL, "")
            .trim()
            .takeIf { it.isNotEmpty() && it.any(Char::isLetter) }
    }

    /**
     * True when [fileArtist] is the same artist as [tagArtist]: equal after folding (case, accents, punctuation,
     * `$` as `s`), or one name of a credit such as `Rui Veloso & Carlos`.
     */
    fun matches(
        tagArtist: String,
        fileArtist: String,
    ): Boolean {
        val tag = TextNormalizer.variants(tagArtist)
        if (tag.isEmpty()) return false
        val credits = listOf(fileArtist) + fileArtist.split(CREDIT_SPLIT)
        return credits.any { credit -> TextNormalizer.variants(credit).any { it in tag } }
    }
}
