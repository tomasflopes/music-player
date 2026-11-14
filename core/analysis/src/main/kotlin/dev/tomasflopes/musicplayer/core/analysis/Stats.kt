package dev.tomasflopes.musicplayer.core.analysis

import kotlin.math.ceil

/** Nearest-rank percentiles, so a reported number is always one that was measured. */
object Stats {
    const val MEDIAN = 0.5
    const val P95 = 0.95

    fun percentile(
        values: List<Long>,
        fraction: Double,
    ): Long {
        require(values.isNotEmpty()) { "No values" }
        val sorted = values.sorted()
        val rank = ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    fun median(values: List<Long>): Long = percentile(values, MEDIAN)

    fun p95(values: List<Long>): Long = percentile(values, P95)
}
