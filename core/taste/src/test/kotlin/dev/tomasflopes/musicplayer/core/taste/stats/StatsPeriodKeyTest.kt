package dev.tomasflopes.musicplayer.core.taste.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.YearMonth

class StatsPeriodKeyTest {
    @Test
    fun `a month key is year and zero padded month, a year key is the year`() {
        assertEquals("2026-03", StatsPeriod.Month(YearMonth.of(2026, 3)).key)
        assertEquals("2026", StatsPeriod.Year(2026).key)
    }

    @Test
    fun `a key parses back to its period`() {
        assertEquals(StatsPeriod.Month(YearMonth.of(2026, 11)), StatsPeriod.parse("2026-11"))
        assertEquals(StatsPeriod.Year(2025), StatsPeriod.parse("2025"))
    }

    @Test
    fun `anything else parses to nothing`() {
        listOf("", "abc", "2026-13", "2026-1", "2026-11-01", "20x6").forEach { assertNull(StatsPeriod.parse(it), it) }
    }
}
