package dev.tomasflopes.musicplayer.core.model.sound

import dev.tomasflopes.musicplayer.core.model.config.IsolationGuardConfig
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The outlier guard (ADR 0087). A song whose sound has few close relatives (its [IsolationGuardConfig.rank]-th nearest
 * distance is above the threshold) gets poor sound neighbours: the nearest songs are all about equally far and the
 * weaker features decide. For such a seed a share of the neighbour slots goes to songs of the seed's own artist.
 * The threshold is the [IsolationGuardConfig.thresholdPercentile] of the same distance over a deterministic sample of
 * the space (evenly spaced rows) and is computed once per [SoundSpace]. Pure Kotlin.
 */
class IsolationGuard(
    private val config: IsolationGuardConfig = IsolationGuardConfig(),
) {
    /** The distance above which a seed is isolated; infinite when the space has no row with enough neighbours. */
    fun threshold(space: SoundSpace): Double = space.memo(config) { computeThreshold(space) }

    /** True when the [IsolationGuardConfig.rank]-th nearest distance of [row] is above [threshold]. */
    fun isIsolated(
        space: SoundSpace,
        row: Int,
    ): Boolean {
        val distance = rankDistance(space, row) ?: return false
        return distance > threshold(space)
    }

    /** How many of [k] slots an isolated seed gives to its own artist. */
    fun artistSlots(k: Int): Int = (k * config.artistShare).roundToInt().coerceIn(0, maxOf(k, 0))

    /**
     * The [k] songs closest to [row] for the evaluation tools, nearest first, skipping rows [accept] refuses. A seed
     * that is not isolated, or has no artist in [artistsOfSong], gets the plain sound list. An isolated one gets
     * [artistSlots] songs whose main or featured artists include its main artist (the nearest of them), the rest
     * nearest by sound, everything ordered by distance. With fewer artist songs than slots the rest is sound.
     */
    fun blend(
        space: SoundSpace,
        row: Int,
        k: Int,
        accept: (Int) -> Boolean,
        artistsOfSong: (Long) -> SongArtistKeys?,
    ): List<Neighbour> {
        val seedArtist = artistsOfSong(space.songIds[row])?.main
        val slots = artistSlots(k)
        val own =
            if (seedArtist == null || slots == 0 || !isIsolated(space, row)) {
                emptyList()
            } else {
                space.nearest(row, slots) {
                    accept(it) && artistsOfSong(space.songIds[it])?.includes(seedArtist) == true
                }
            }
        if (own.isEmpty()) return space.nearest(row, k, accept)
        val taken = own.mapTo(HashSet()) { it.row }
        val rest = space.nearest(row, k - own.size) { accept(it) && it !in taken }
        return (own + rest).sortedWith(compareBy<Neighbour> { it.distance }.thenBy { it.songId })
    }

    /** The rows the threshold is measured on: all of them, or [sampleSize] evenly spaced ones. */
    internal fun sampleRows(
        size: Int,
        sampleSize: Int = config.sampleSize,
    ): IntArray =
        if (size <= sampleSize) {
            IntArray(size) { it }
        } else {
            IntArray(sampleSize) { (it.toLong() * size / sampleSize).toInt() }
        }

    private fun computeThreshold(space: SoundSpace): Double {
        val distances = sampleRows(space.size).asList().mapNotNull { rankDistance(space, it) }.toDoubleArray()
        if (distances.isEmpty()) return Double.POSITIVE_INFINITY
        distances.sort()
        val position = config.thresholdPercentile / PERCENT * (distances.size - 1)
        val lower = floor(position).toInt()
        val upper = minOf(lower + 1, distances.size - 1)
        return distances[lower] + (distances[upper] - distances[lower]) * (position - lower)
    }

    /** The distance from [row] to its [IsolationGuardConfig.rank]-th nearest other row; null when there are fewer. */
    private fun rankDistance(
        space: SoundSpace,
        row: Int,
    ): Double? {
        val rank = config.rank
        // The smallest distances so far, ascending; unfilled places stay infinite.
        val best = DoubleArray(rank) { Double.POSITIVE_INFINITY }
        for (other in 0 until space.size) {
            val distance = if (other == row) Double.POSITIVE_INFINITY else space.distance(row, other)
            if (distance < best[rank - 1]) insert(best, distance)
        }
        return best[rank - 1].takeIf { it.isFinite() }
    }

    /** Puts [distance] into the ascending [best], dropping the largest. */
    private fun insert(
        best: DoubleArray,
        distance: Double,
    ) {
        var i = best.size - 1
        while (i > 0 && best[i - 1] > distance) {
            best[i] = best[i - 1]
            i--
        }
        best[i] = distance
    }

    private companion object {
        const val PERCENT = 100.0
    }
}
