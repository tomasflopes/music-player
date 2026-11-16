package dev.tomasflopes.musicplayer.core.taste.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MixConfigTest {
    @Test
    fun `the defaults are a batch of 25 with 30 percent exploration from the 600 nearest`() {
        val config = MixConfig()

        assertEquals(25, config.batchSize)
        assertEquals(0.30, config.explorationShare)
        assertEquals(600, config.explorationBand)
    }

    @Test
    fun `the builder config carries the same numbers and leaves the radio defaults alone`() {
        val builder = MixConfig().builderConfig()

        assertEquals(25, builder.batchSize)
        assertEquals(0.30, builder.explorationShare)
        assertEquals(0.70, builder.nearestShare, 1e-9)
        assertEquals(600, builder.explorationBand)
        assertEquals(150, RadioConfig().explorationBand)
        assertEquals(0.12, RadioConfig().explorationShare)
    }

    @Test
    fun `a band that cannot hold a batch is refused`() {
        assertThrows(IllegalArgumentException::class.java) { MixConfig(explorationBand = 10) }
    }
}
