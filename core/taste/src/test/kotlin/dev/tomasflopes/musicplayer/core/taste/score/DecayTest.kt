package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.taste.config.DecayRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DecayTest {
    private val decay = Decay(DecayRule())
    private val day = 86_400_000L

    @Test
    fun `no age keeps the full weight`() {
        assertEquals(1.0, decay.factor(0))
    }

    @Test
    fun `one half-life halves it and two quarter it`() {
        assertEquals(0.5, decay.factor(30 * day))
        assertEquals(0.25, decay.factor(60 * day))
    }

    @Test
    fun `a row from the future counts as age zero`() {
        assertEquals(1.0, decay.factor(-5_000))
    }

    @Test
    fun `the half-life comes from the rule`() {
        assertEquals(0.5, Decay(DecayRule(halfLifeDays = 10.0)).factor(10 * day))
    }

    @Test
    fun `the horizon is the rule's days in milliseconds`() {
        assertEquals(240 * day, DecayRule().horizonMs)
    }
}
