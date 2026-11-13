package dev.tomasflopes.musicplayer.core.model

/**
 * What to show when a tag is missing (ADR 0007). Tags always win. An untagged `Artist - Title.mp3` gives both
 * fields; any other file name becomes the title, and the artist is [UNKNOWN_ARTIST].
 */
object DisplayFallbacks {
    const val UNKNOWN_ARTIST = "Unknown artist"
    private const val SEPARATOR = " - "
    private val TRACK_NUMBER = Regex("\\d+\\.?")

    fun title(
        tagTitle: String?,
        fileName: String,
    ): String = tagTitle.present() ?: artistAndTitle(fileName)?.second ?: baseName(fileName)

    fun artist(
        tagArtist: String?,
        fileName: String,
    ): String = tagArtist.present() ?: artistAndTitle(fileName)?.first ?: UNKNOWN_ARTIST

    private fun String?.present(): String? = this?.takeIf { it.isNotBlank() }

    /** Split on the first ` - `. A bare number on the left (`01 - Intro`) is a track number, not an artist. */
    private fun artistAndTitle(fileName: String): Pair<String, String>? {
        val name = baseName(fileName)
        val separator = name.indexOf(SEPARATOR)
        val artist = if (separator < 0) "" else name.substring(0, separator).trim()
        val title = if (separator < 0) "" else name.substring(separator + SEPARATOR.length).trim()
        val isArtist = artist.isNotEmpty() && !TRACK_NUMBER.matches(artist)
        return (artist to title).takeIf { isArtist && title.isNotEmpty() }
    }

    private fun baseName(fileName: String): String =
        (if ('.' in fileName) fileName.substringBeforeLast('.') else fileName).trim()
}
