package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.stats.TimeBucketPlays
import java.time.Instant
import java.time.ZoneId

/** Plays by local hour of the day, 24 entries from midnight. */
data class ListeningClock(
    val playsByHour: List<Int>,
) {
    init {
        require(playsByHour.size == HOURS) { "a clock has $HOURS hours: ${playsByHour.size}" }
    }

    val total: Int get() = playsByHour.sum()

    /** The busiest hour, the earliest on a tie; null when nothing was played. */
    val peakHour: Int? get() = if (total == 0) null else playsByHour.indices.maxBy { playsByHour[it] }

    companion object {
        const val HOURS = 24
        val EMPTY = ListeningClock(List(HOURS) { 0 })

        /** Each bucket counts in the local hour its start falls in, so daylight saving follows the date. */
        fun from(
            buckets: List<TimeBucketPlays>,
            zone: ZoneId,
        ): ListeningClock {
            val hours = IntArray(HOURS)
            buckets.forEach { hours[Instant.ofEpochMilli(it.bucketStartEpochMs).atZone(zone).hour] += it.plays }
            return ListeningClock(hours.toList())
        }
    }
}
