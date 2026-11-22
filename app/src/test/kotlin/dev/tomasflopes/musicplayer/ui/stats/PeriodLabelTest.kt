package dev.tomasflopes.musicplayer.ui.stats

import dev.tomasflopes.musicplayer.core.taste.stats.StatsPeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.util.Locale

class PeriodLabelTest {
    @Test
    fun `a month reads as its name and year`() =
        assertEquals("November 2026", periodLabel(StatsPeriod.Month(YearMonth.of(2026, 11)), Locale.ENGLISH))

    @Test
    fun `a year reads as the year`() = assertEquals("2025", periodLabel(StatsPeriod.Year(2025), Locale.ENGLISH))
}
