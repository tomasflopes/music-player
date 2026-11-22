package dev.tomasflopes.musicplayer.core.taste.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StatsConfigTest {
    @Test
    fun `defaults are the approved starting values`() {
        val config = StatsConfig()

        assertEquals(5, config.topSize)
        assertEquals(2, config.minSkips)
        assertEquals(2, config.comebackMinRise)
        assertEquals(3_600_000L, config.closedAfterMs)
    }

    @Test
    fun `nonsense values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { StatsConfig(topSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { StatsConfig(minSkips = 0) }
        assertThrows(IllegalArgumentException::class.java) { StatsConfig(comebackMinRise = 0) }
        assertThrows(IllegalArgumentException::class.java) { StatsConfig(closedAfterMs = -1) }
    }
}
