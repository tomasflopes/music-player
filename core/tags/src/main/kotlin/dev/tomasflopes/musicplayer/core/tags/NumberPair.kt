package dev.tomasflopes.musicplayer.core.tags

/** Parses `TRCK` / `TPOS` text: `8`, `08`, `8/14`. Zero, text and anything unreadable mean "not tagged". */
internal object NumberPair {
    private const val SEPARATOR = '/'

    /** Returns (number, total). */
    fun parse(text: String?): Pair<Int?, Int?> {
        val parts = text.orEmpty().split(SEPARATOR, limit = 2)
        return positive(parts[0]) to positive(parts.getOrNull(1))
    }

    private fun positive(part: String?): Int? =
        part
            ?.trim()
            ?.takeIf { digits -> digits.all { it in '0'..'9' } }
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
}
