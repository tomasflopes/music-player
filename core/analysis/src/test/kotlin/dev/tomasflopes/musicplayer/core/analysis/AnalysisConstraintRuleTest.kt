package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnalysisConstraintRuleTest {
    @Test
    fun `nothing waiting or a few songs need no constraints`() {
        assertEquals(false, AnalysisConstraintRule.requiresCharging(0))
        assertEquals(false, AnalysisConstraintRule.requiresCharging(3))
        assertEquals(false, AnalysisConstraintRule.requiresCharging(100))
    }

    @Test
    fun `above one hundred songs the job waits for the charger`() {
        assertEquals(true, AnalysisConstraintRule.requiresCharging(101))
        assertEquals(true, AnalysisConstraintRule.requiresCharging(14_165))
    }

    @Test
    fun `a batch is shorter than the ten minutes a job may run`() {
        assertEquals(true, AnalysisConstraintRule.BATCH_MINUTES < 10)
    }
}
