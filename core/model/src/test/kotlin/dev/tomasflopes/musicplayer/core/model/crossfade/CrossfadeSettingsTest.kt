package dev.tomasflopes.musicplayer.core.model.crossfade

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class CrossfadeSettingsTest {
    @Test
    fun `is off and four seconds by default`() {
        val settings = CrossfadeSettings()

        assertFalse(settings.enabled)
        assertEquals(4_000L, settings.durationMs)
    }

    @Test
    fun `the range is one to twelve seconds`() {
        assertEquals(1_000L..12_000L, CrossfadeSettings.DURATION_RANGE_MS)
    }

    @Test
    fun `coerced pulls the length into the range and keeps the switch`() {
        assertEquals(CrossfadeSettings(true, 12_000), CrossfadeSettings(true, 99_000).coerced())
        assertEquals(CrossfadeSettings(false, 1_000), CrossfadeSettings(false, 0).coerced())
        assertEquals(CrossfadeSettings(true, 5_000), CrossfadeSettings(true, 5_000).coerced())
    }
}
