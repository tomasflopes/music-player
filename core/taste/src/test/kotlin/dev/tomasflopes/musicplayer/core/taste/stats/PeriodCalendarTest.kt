package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.stats.TimeRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.time.ZoneId

class PeriodCalendarTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val november = StatsPeriod.Month(YearMonth.of(2026, 11))

    @Test
    fun `a month is half-open from its first midnight to the next`() {
        assertEquals(
            TimeRange(epoch("2026-11-01T00:00:00Z"), epoch("2026-12-01T00:00:00Z")),
            november.range(UTC),
        )
    }

    @Test
    fun `a month starts at the local midnight of the zone`() {
        assertEquals(
            TimeRange(epoch("2026-10-31T15:00:00Z"), epoch("2026-11-30T15:00:00Z")),
            november.range(tokyo),
        )
    }

    @Test
    fun `a month that loses an hour to daylight saving is an hour shorter`() {
        val march = StatsPeriod.Month(YearMonth.of(2026, 3)).range(lisbon)

        assertEquals(epoch("2026-03-01T00:00:00Z"), march.startEpochMs)
        assertEquals(epoch("2026-03-31T23:00:00Z"), march.endEpochMs)
    }

    @Test
    fun `a year runs from the first midnight of January to the next`() {
        assertEquals(
            TimeRange(epoch("2025-12-31T15:00:00Z"), epoch("2026-12-31T15:00:00Z")),
            StatsPeriod.Year(2026).range(tokyo),
        )
    }

    @Test
    fun `the instant a month starts belongs to it and the instant before to the previous one`() {
        val calendar = PeriodCalendar(UTC)
        val start = epoch("2026-11-01T00:00:00Z")

        assertEquals(november, calendar.containing(PeriodKind.MONTH, start))
        assertEquals(november.previous(), calendar.containing(PeriodKind.MONTH, start - 1))
        assertEquals(november.next(), calendar.containing(PeriodKind.MONTH, november.range(UTC).endEpochMs))
    }

    @Test
    fun `the same instant is in different months in different zones`() {
        val instant = epoch("2026-10-31T23:59:00Z")

        assertEquals(
            StatsPeriod.Month(YearMonth.of(2026, 10)),
            PeriodCalendar(UTC).containing(PeriodKind.MONTH, instant),
        )
        assertEquals(november, PeriodCalendar(tokyo).containing(PeriodKind.MONTH, instant))
    }

    @Test
    fun `December runs into January and January back into December`() {
        assertEquals(StatsPeriod.Month(YearMonth.of(2027, 1)), StatsPeriod.Month(YearMonth.of(2026, 12)).next())
        assertEquals(StatsPeriod.Month(YearMonth.of(2026, 12)), StatsPeriod.Month(YearMonth.of(2027, 1)).previous())
        assertEquals(StatsPeriod.Year(2025), StatsPeriod.Year(2026).previous())
    }

    @Test
    fun `past months are the months before this one, newest first, back to the first play`() {
        val months =
            PeriodCalendar(UTC).pastMonths(epoch("2026-08-15T10:00:00Z"), epoch("2026-11-22T10:00:00Z"))

        assertEquals(listOf(10, 9, 8), months.map { (it as StatsPeriod.Month).value.monthValue })
    }

    @Test
    fun `there are no past months when the first play is this month or the log is empty`() {
        val now = epoch("2026-11-22T10:00:00Z")

        assertEquals(emptyList<StatsPeriod>(), PeriodCalendar(UTC).pastMonths(epoch("2026-11-02T10:00:00Z"), now))
        assertEquals(emptyList<StatsPeriod>(), PeriodCalendar(UTC).pastMonths(null, now))
    }

    @Test
    fun `years run from this year back to the year of the first play`() {
        val years = PeriodCalendar(UTC).years(epoch("2024-06-01T00:00:00Z"), epoch("2026-11-22T10:00:00Z"))

        assertEquals(listOf(StatsPeriod.Year(2026), StatsPeriod.Year(2025), StatsPeriod.Year(2024)), years)
    }

    @Test
    fun `a period is closed once its end and the grace have passed`() {
        val calendar = PeriodCalendar(UTC)
        val end = november.range(UTC).endEpochMs

        assertFalse(calendar.isClosed(november, end + 3_599_999, graceMs = 3_600_000))
        assertTrue(calendar.isClosed(november, end + 3_600_000, graceMs = 3_600_000))
        assertFalse(calendar.isClosed(november, end - 1, graceMs = 0))
    }
}
