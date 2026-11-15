package dev.tomasflopes.musicplayer.core.taste.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RewindConfigTest {
    @Test
    fun `the defaults are the starting numbers`() {
        val config = RewindConfig()

        assertEquals(60, config.quietDays)
        assertEquals(60 * 86_400_000L, config.quietMs)
        assertEquals(2.0, config.minScoreThen)
        assertEquals(50, config.size)
        assertEquals(3, config.maxPerArtist)
        assertEquals(SmartShuffleConfig().artistGap, config.artistGap)
        assertEquals(6.0, config.tasteScale)
        assertEquals(0.3, config.randomWeight)
    }

    @Test
    fun `bad numbers are refused`() {
        refused { RewindConfig(quietDays = 0) }
        refused { RewindConfig(minScoreThen = Double.NaN) }
        refused { RewindConfig(size = 0) }
        refused { RewindConfig(maxPerArtist = 0) }
        refused { RewindConfig(artistGap = -1) }
        refused { RewindConfig(artistGap = SmartShuffleConfig.MAX_ARTIST_GAP + 1) }
        refused { RewindConfig(tasteScale = 0.0) }
        refused { RewindConfig(randomWeight = -0.1) }
        refused { RewindConfig(randomWeight = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `a gap of zero, no random part and a negative minimum are allowed`() {
        RewindConfig(artistGap = 0, randomWeight = 0.0, minScoreThen = -1.0)
    }

    private fun refused(build: () -> Any) {
        assertThrows(IllegalArgumentException::class.java) { build() }
    }
}
