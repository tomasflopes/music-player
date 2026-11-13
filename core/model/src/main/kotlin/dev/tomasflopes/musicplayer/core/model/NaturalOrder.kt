package dev.tomasflopes.musicplayer.core.model

import java.util.Locale

/**
 * File-name order as people expect it: case is ignored and digit runs compare as numbers (`2` before `10`).
 * Only identical strings compare equal, so sorting is the same every time.
 */
object NaturalOrder : Comparator<String> {
    private val CHUNK = Regex("\\d+|\\D+")

    override fun compare(
        a: String,
        b: String,
    ): Int {
        val left = CHUNK.findAll(a).map { it.value }.toList()
        val right = CHUNK.findAll(b).map { it.value }.toList()
        var leadingZeros = 0
        for (index in 0 until minOf(left.size, right.size)) {
            val x = left[index]
            val y = right[index]
            val bothNumbers = x[0].isDigit() && y[0].isDigit()
            val byValue = if (bothNumbers) compareNumbers(x, y) else compareText(x, y)
            if (byValue != 0) return byValue
            if (bothNumbers && leadingZeros == 0) leadingZeros = x.length.compareTo(y.length)
        }
        return left.size.compareTo(right.size).takeIf { it != 0 } ?: leadingZeros.takeIf { it != 0 } ?: a.compareTo(b)
    }

    private fun compareText(
        x: String,
        y: String,
    ): Int = x.lowercase(Locale.ROOT).compareTo(y.lowercase(Locale.ROOT))

    /** By value, without parsing: any length works. */
    private fun compareNumbers(
        x: String,
        y: String,
    ): Int {
        val a = x.trimStart('0')
        val b = y.trimStart('0')
        return a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
    }
}
