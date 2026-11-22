package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.stats.TimeRange
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class PeriodKind { MONTH, YEAR }

/** A calendar month or year, whose bounds are the local midnights of a zone (half-open, so edges are exact). */
sealed interface StatsPeriod {
    val kind: PeriodKind

    fun range(zone: ZoneId): TimeRange

    fun previous(): StatsPeriod

    fun next(): StatsPeriod

    /** A short text that names the period in routes and parses back with [parse]: `2026-11` or `2026`. */
    val key: String

    companion object {
        private val MONTH_KEY = Regex("""(\d{4})-(0[1-9]|1[0-2])""")
        private val YEAR_KEY = Regex("""\d{4}""")

        /** The period a [key] names, or null when it names none. */
        fun parse(key: String): StatsPeriod? =
            MONTH_KEY
                .matchEntire(
                    key,
                )?.let { Month(YearMonth.of(it.groupValues[1].toInt(), it.groupValues[2].toInt())) }
                ?: if (YEAR_KEY.matches(key)) Year(key.toInt()) else null
    }

    data class Month(
        val value: YearMonth,
    ) : StatsPeriod {
        override val kind: PeriodKind get() = PeriodKind.MONTH

        override val key: String get() = "%04d-%02d".format(value.year, value.monthValue)

        override fun range(zone: ZoneId) =
            TimeRange(startOf(value.atDay(1), zone), startOf(value.plusMonths(1).atDay(1), zone))

        override fun previous() = Month(value.minusMonths(1))

        override fun next() = Month(value.plusMonths(1))
    }

    data class Year(
        val value: Int,
    ) : StatsPeriod {
        override val kind: PeriodKind get() = PeriodKind.YEAR

        override val key: String get() = "%04d".format(value)

        override fun range(zone: ZoneId) =
            TimeRange(startOf(LocalDate.of(value, 1, 1), zone), startOf(LocalDate.of(value + 1, 1, 1), zone))

        override fun previous() = Year(value - 1)

        override fun next() = Year(value + 1)
    }
}

private fun startOf(
    day: LocalDate,
    zone: ZoneId,
): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
