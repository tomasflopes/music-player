package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.SoundVectorizer
import java.util.Locale

/** One neighbour of the seed: its [path], distance and the weighted, normalised values, one per dimension. */
class SimilarRow(
    val path: String,
    val distance: Double,
    val values: FloatArray,
)

/** The text `analyser similar` prints: the seed first (distance 0), then its neighbours nearest first. */
object SimilarFormat {
    fun format(
        seedPath: String,
        seedValues: FloatArray,
        rows: List<SimilarRow>,
    ): String =
        buildString {
            append("seed: ").append(seedPath).append('\n')
            append("columns: ").append(SoundVectorizer.NAMES.joinToString(" ")).append(" (normalised, weighted)\n")
            append(line(0, 0.0, seedValues, seedPath))
            rows.forEachIndexed { index, row -> append(line(index + 1, row.distance, row.values, row.path)) }
            if (rows.isEmpty()) append("no other songs\n")
        }

    private fun line(
        rank: Int,
        distance: Double,
        values: FloatArray,
        path: String,
    ): String {
        val numbers = values.joinToString(" ") { String.format(Locale.ROOT, "%.2f", it) }
        return String.format(Locale.ROOT, "%2d  d=%.3f  %s  %s\n", rank, distance, numbers, path)
    }
}
