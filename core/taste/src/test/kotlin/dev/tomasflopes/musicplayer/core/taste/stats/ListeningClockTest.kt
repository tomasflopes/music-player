package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.stats.ListeningAggregates.Companion.BUCKET_MS
import dev.tomasflopes.musicplayer.core.model.stats.TimeBucketPlays
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.ZoneId

class ListeningClockTest {
    private fun bucket(
        iso: String,
        plays: Int,
    ) = TimeBucketPlays(epoch(iso) / BUCKET_MS * BUCKET_MS, plays)

    @Test
    fun `plays land in the local hour of the zone`() {
        val clock = ListeningClock.from(listOf(bucket("2026-11-10T23:00:00Z", 3)), ZoneId.of("Asia/Tokyo"))

        assertEquals(3, clock.playsByHour[8])
        assertEquals(3, clock.total)
    }

    @Test
    fun `daylight saving moves the hour with the date`() {
        val lisbon = ZoneId.of("Europe/Lisbon")
        val clock =
            ListeningClock.from(
                listOf(bucket("2026-01-10T12:00:00Z", 1), bucket("2026-07-10T12:00:00Z", 2)),
                lisbon,
            )

        assertEquals(1, clock.playsByHour[12])
        assertEquals(2, clock.playsByHour[13])
    }

    @Test
    fun `a half-hour zone uses the hour the quarter-hour bucket starts in`() {
        val clock = ListeningClock.from(listOf(bucket("2026-11-10T00:15:00Z", 4)), ZoneId.of("Asia/Kolkata"))

        assertEquals(4, clock.playsByHour[5])
    }

    @Test
    fun `buckets of the same hour add up and the clock always has 24 hours`() {
        val clock =
            ListeningClock.from(
                listOf(bucket("2026-11-10T09:00:00Z", 1), bucket("2026-11-11T09:45:00Z", 2)),
                ZoneId.of("UTC"),
            )

        assertEquals(24, clock.playsByHour.size)
        assertEquals(3, clock.playsByHour[9])
    }

    @Test
    fun `the peak hour is the busiest one, the earliest on a tie, and none when empty`() {
        val busy =
            ListeningClock.from(
                listOf(bucket("2026-11-10T21:00:00Z", 5), bucket("2026-11-10T07:00:00Z", 5)),
                UTC,
            )

        assertEquals(7, busy.peakHour)
        assertNull(ListeningClock.from(emptyList(), UTC).peakHour)
    }
}
