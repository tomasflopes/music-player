package dev.tomasflopes.musicplayer.core.analysis.contract

/** One measured window of one fixture, taken from the lossless source, not from a decoder. */
class ReferenceWindow(
    val startMs: Long,
    val lengthMs: Long,
    val monoRms: Double,
)

/** What is true of one fixture: its format, length, where the first click sits and the windows. */
class ReferenceFile(
    val name: String,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long,
    /** Song time of the first sample above 0.2, or -1 when the file has no clicks. */
    val onsetMs: Long,
    val windows: List<ReferenceWindow>,
)

/** Parses `reference.tsv`; the columns are listed in its first line. */
object ReferenceTable {
    private const val COLUMNS = 8

    fun parse(text: String): List<ReferenceFile> {
        val rows =
            text
                .lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split('\t') }
        rows.forEach { check(it.size == COLUMNS) { "Bad reference row: $it" } }
        return rows.groupBy { it[0] }.map { (name, group) ->
            val first = group.first()
            ReferenceFile(
                name = name,
                sampleRate = first[1].toInt(),
                channels = first[2].toInt(),
                durationMs = first[3].toLong(),
                onsetMs = first[4].toLong(),
                windows = group.map { ReferenceWindow(it[5].toLong(), it[6].toLong(), it[7].toDouble()) },
            )
        }
    }
}
