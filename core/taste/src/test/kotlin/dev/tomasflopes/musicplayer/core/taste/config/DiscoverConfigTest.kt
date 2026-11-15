package dev.tomasflopes.musicplayer.core.taste.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DiscoverConfigTest {
    @Test
    fun `the defaults are the starting numbers`() {
        val config = DiscoverConfig()

        assertEquals(50, config.size)
        assertEquals(3, config.maxPerArtist)
        assertEquals(SmartShuffleConfig().artistGap, config.artistGap)
        assertEquals(6.0, config.tasteScale)
        assertEquals(0.5, config.randomWeight)
    }

    @Test
    fun `bad numbers are refused`() {
        refused { DiscoverConfig(size = 0) }
        refused { DiscoverConfig(maxPerArtist = 0) }
        refused { DiscoverConfig(artistGap = -1) }
        refused { DiscoverConfig(artistGap = SmartShuffleConfig.MAX_ARTIST_GAP + 1) }
        refused { DiscoverConfig(tasteScale = 0.0) }
        refused { DiscoverConfig(tasteScale = Double.NaN) }
        refused { DiscoverConfig(randomWeight = -0.1) }
        refused { DiscoverConfig(randomWeight = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `a gap of zero and no random part are allowed`() {
        DiscoverConfig(artistGap = 0, randomWeight = 0.0)
    }

    private fun refused(build: () -> Any) {
        assertThrows(IllegalArgumentException::class.java) { build() }
    }
}
