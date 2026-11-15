package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnalysisHintRuleTest {
    @Test
    fun `a fresh long queue with nothing analysed shows the hint`() {
        assertEquals(true, AnalysisHintRule.shouldShow(profiled = 0, waiting = 14_165, dismissed = false))
    }

    @Test
    fun `a closed hint stays closed`() {
        assertEquals(false, AnalysisHintRule.shouldShow(profiled = 0, waiting = 14_165, dismissed = true))
    }

    @Test
    fun `an import or any analysed song makes the hint pointless`() {
        assertEquals(false, AnalysisHintRule.shouldShow(profiled = 14_164, waiting = 1, dismissed = false))
        assertEquals(false, AnalysisHintRule.shouldShow(profiled = 1, waiting = 14_000, dismissed = false))
    }

    @Test
    fun `a short queue is not worth a hint`() {
        assertEquals(false, AnalysisHintRule.shouldShow(profiled = 0, waiting = 100, dismissed = false))
    }
}
