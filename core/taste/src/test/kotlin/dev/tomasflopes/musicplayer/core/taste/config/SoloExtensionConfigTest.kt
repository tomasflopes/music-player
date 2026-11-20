package dev.tomasflopes.musicplayer.core.taste.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SoloExtensionConfigTest {
    @Test
    fun `the defaults are a batch of 5 refilled at 2 or fewer left`() {
        val config = SoloExtensionConfig()

        assertEquals(5, config.batchSize)
        assertEquals(2, config.lowWater)
        assertEquals(RadioConfig().explorationShare, config.explorationShare)
    }

    @Test
    fun `a low water mark at or above the batch size is refused`() {
        assertThrows(IllegalArgumentException::class.java) { SoloExtensionConfig(batchSize = 5, lowWater = 5) }
    }

    @Test
    fun `a negative low water mark is refused`() {
        assertThrows(IllegalArgumentException::class.java) { SoloExtensionConfig(lowWater = -1) }
    }

    @Test
    fun `an empty batch is refused`() {
        assertThrows(IllegalArgumentException::class.java) { SoloExtensionConfig(batchSize = 0, lowWater = 0) }
    }
}
