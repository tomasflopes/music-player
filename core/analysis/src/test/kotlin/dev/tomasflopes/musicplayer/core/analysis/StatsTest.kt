package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StatsTest {
    private val hundred = (1L..100L).toList().shuffled(java.util.Random(3))

    @Test
    fun `median and p95 are nearest-rank values`() {
        assertEquals(50L, Stats.median(hundred))
        assertEquals(95L, Stats.p95(hundred))
    }

    @Test
    fun `one value is every percentile`() {
        assertEquals(7L, Stats.median(listOf(7L)))
        assertEquals(7L, Stats.p95(listOf(7L)))
    }

    @Test
    fun `no values is an error`() {
        assertThrows(IllegalArgumentException::class.java) { Stats.median(emptyList()) }
    }
}
