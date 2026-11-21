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

    @Test
    fun `refill starts at 5 or fewer songs left and must stay below the batch size`() {
        assertEquals(5, MixConfig().refillLowWater)
        assertThrows(IllegalArgumentException::class.java) { MixConfig(refillLowWater = 25) }
    }

    @Test
    fun `learning defaults`() {
        val config = MixConfig()

        assertEquals(0.5, config.likedMinScore)
        assertEquals(15, config.likedMax)
        assertEquals(1.5, config.likedGate)
        assertEquals(-3.0, config.bannedScore)
        assertEquals(14, config.mixRepeatDays)
        assertEquals(1.0, config.mixRepeatPenalty)
        assertEquals(0.10, config.explorationFloor)
        assertEquals(200, config.explorationRows)
    }

    @Test
    fun `exploration falls from 30 percent to 10 percent over 200 informative rows and stays there`() {
        val config = MixConfig()

        assertEquals(0.30, config.explorationShareFor(0), 1e-9)
        assertEquals(0.20, config.explorationShareFor(100), 1e-9)
        assertEquals(0.10, config.explorationShareFor(200), 1e-9)
        assertEquals(0.10, config.explorationShareFor(5_000), 1e-9)
    }

    @Test
    fun `a mix configured with less exploration than the floor never rises above its share`() {
        val config = MixConfig(explorationShare = 0.0)

        assertEquals(0.0, config.explorationShareFor(0))
        assertEquals(0.0, config.explorationShareFor(200))
    }

    @Test
    fun `the builder config can carry a share other than the start share`() {
        val builder = MixConfig().builderConfig(explorationShare = 0.1)

        assertEquals(0.1, builder.explorationShare)
        assertEquals(0.9, builder.nearestShare, 1e-9)
    }

    @Test
    fun `learning numbers that make no sense are refused`() {
        fun refused(build: () -> Any) = assertThrows(IllegalArgumentException::class.java) { build() }
        refused { MixConfig(likedMinScore = 0.0) }
        refused { MixConfig(likedMinScore = Double.NaN) }
        refused { MixConfig(likedMax = -1) }
        refused { MixConfig(likedGate = 0.0) }
        refused { MixConfig(likedGate = Double.POSITIVE_INFINITY) }
        refused { MixConfig(bannedScore = 0.0) }
        refused { MixConfig(bannedScore = Double.NaN) }
        refused { MixConfig(mixRepeatDays = -1) }
        refused { MixConfig(mixRepeatPenalty = -0.1) }
        refused { MixConfig(mixRepeatPenalty = Double.NaN) }
        refused { MixConfig(explorationFloor = -0.1) }
        refused { MixConfig(explorationFloor = 1.1) }
        refused { MixConfig(explorationRows = 0) }
    }
}
