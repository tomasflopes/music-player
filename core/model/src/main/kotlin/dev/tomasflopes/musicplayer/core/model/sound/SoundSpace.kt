package dev.tomasflopes.musicplayer.core.model.sound

import java.util.PriorityQueue
import kotlin.math.sqrt

/** One result of a nearest-song search: the row in the space, the song, and its distance to the query. */
data class Neighbour(
    val row: Int,
    val songId: Long,
    val distance: Double,
)

/**
 * The sound of the library as points (ADR 0068, 0069): [songIds] (one per row), [dim] floats per song, all in
 * [values] row by row. The values are already normalised to 0..1 and weighted, so the distance between two rows is
 * plain Euclidean. Pure Kotlin; nothing here knows what the dimensions mean.
 */
class SoundSpace(
    val songIds: LongArray,
    val dim: Int,
    val values: FloatArray,
) {
    init {
        require(dim >= 1) { "dim must be at least 1: $dim" }
        require(values.size == songIds.size * dim) {
            "Expected ${songIds.size} songs x $dim values, got ${values.size}"
        }
    }

    val size: Int get() = songIds.size

    private val rowOfSong: Map<Long, Int> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        HashMap<Long, Int>(size * 2).also { map -> songIds.forEachIndexed { row, id -> map[id] = row } }
    }

    /** The row of [songId], or -1 when the space has no point for it. */
    fun indexOf(songId: Long): Int = rowOfSong[songId] ?: -1

    fun vector(row: Int): FloatArray = values.copyOfRange(row * dim, row * dim + dim)

    fun distance(
        a: Int,
        b: Int,
    ): Double {
        var sum = 0.0
        for (d in 0 until dim) {
            val diff = (values[a * dim + d] - values[b * dim + d]).toDouble()
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /** The mean point of [rows] ([dim] values), for example the sound of an artist. [rows] must not be empty. */
    fun centroid(rows: List<Int>): DoubleArray {
        require(rows.isNotEmpty()) { "A centroid needs at least one row" }
        val sum = DoubleArray(dim)
        for (row in rows) {
            for (d in 0 until dim) sum[d] += values[row * dim + d].toDouble()
        }
        for (d in 0 until dim) sum[d] /= rows.size
        return sum
    }

    /** Distance from [row] to an arbitrary point of [dim] values (a cluster centre, say). */
    fun distanceTo(
        row: Int,
        point: DoubleArray,
    ): Double {
        require(point.size == dim) { "Point has ${point.size} values, the space has $dim" }
        var sum = 0.0
        for (d in 0 until dim) {
            val diff = values[row * dim + d] - point[d]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * The [k] rows closest to [row], nearest first, never [row] itself. Rows for which [accept] is false are
     * skipped. Equal distances go to the lower song id, so the answer does not depend on row order.
     */
    fun nearest(
        row: Int,
        k: Int,
        accept: (Int) -> Boolean = { true },
    ): List<Neighbour> = nearestBy(k, { it != row && accept(it) }) { distance(row, it) }

    /** Same as [nearest], to an arbitrary [point]. */
    fun nearestTo(
        point: DoubleArray,
        k: Int,
        accept: (Int) -> Boolean = { true },
    ): List<Neighbour> = nearestBy(k, accept) { distanceTo(it, point) }

    private fun nearestBy(
        k: Int,
        accept: (Int) -> Boolean,
        distance: (Int) -> Double,
    ): List<Neighbour> {
        if (k <= 0) return emptyList()
        // A max-heap of the best k so far: the head is the worst of them and leaves first.
        val worstFirst = compareByDescending<Neighbour> { it.distance }.thenByDescending { it.songId }
        val heap = PriorityQueue(k, worstFirst)
        for (row in 0 until size) {
            if (!accept(row)) continue
            val candidate = Neighbour(row, songIds[row], distance(row))
            if (heap.size < k) {
                heap.add(candidate)
            } else if (worstFirst.compare(candidate, heap.peek()) > 0) {
                heap.poll()
                heap.add(candidate)
            }
        }
        return heap.sortedWith(compareBy<Neighbour> { it.distance }.thenBy { it.songId })
    }
}
