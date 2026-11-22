package dev.tomasflopes.musicplayer.core.taste.stats

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Which period an instant is in, which periods have data, and which are closed, all in one zone. */
class PeriodCalendar(
    private val zone: ZoneId,
) {
    fun containing(
        kind: PeriodKind,
        atEpochMs: Long,
    ): StatsPeriod {
        val local = Instant.ofEpochMilli(atEpochMs).atZone(zone)
        return when (kind) {
            PeriodKind.MONTH -> StatsPeriod.Month(YearMonth.from(local))
            PeriodKind.YEAR -> StatsPeriod.Year(local.year)
        }
    }

    /** The months before the current one back to the month of [earliestPlayAtEpochMs], newest first. */
    fun pastMonths(
        earliestPlayAtEpochMs: Long?,
        nowEpochMs: Long,
    ): List<StatsPeriod> {
        if (earliestPlayAtEpochMs == null) return emptyList()
        val first = YearMonth.from(Instant.ofEpochMilli(earliestPlayAtEpochMs).atZone(zone))
        val months = mutableListOf<StatsPeriod>()
        var month = YearMonth.from(Instant.ofEpochMilli(nowEpochMs).atZone(zone)).minusMonths(1)
        while (!month.isBefore(first)) {
            months += StatsPeriod.Month(month)
            month = month.minusMonths(1)
        }
        return months
    }

    /** The current year back to the year of [earliestPlayAtEpochMs], newest first. */
    fun years(
        earliestPlayAtEpochMs: Long?,
        nowEpochMs: Long,
    ): List<StatsPeriod> {
        if (earliestPlayAtEpochMs == null) return emptyList()
        val first = Instant.ofEpochMilli(earliestPlayAtEpochMs).atZone(zone).year
        val last = Instant.ofEpochMilli(nowEpochMs).atZone(zone).year
        return (last downTo first).map { StatsPeriod.Year(it) }
    }

    /** True once [graceMs] have passed since the period ended: no row can still arrive for it. */
    fun isClosed(
        period: StatsPeriod,
        nowEpochMs: Long,
        graceMs: Long,
    ): Boolean = nowEpochMs >= period.range(zone).endEpochMs + graceMs
}
