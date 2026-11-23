package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every [TempoReliability] meets the contract (ADR 0098); a new rule adds one line here. */
class TempoReliabilityContractTest {
    private fun failures(factory: () -> TempoReliability) =
        TempoReliabilityContract
            .checks(factory)
            .mapNotNull { check ->
                runCatching { check.run() }.exceptionOrNull()?.let { "${check.name}: ${it.message}" }
            }

    @Test
    fun `the stored confidence rule meets the contract`() {
        val failed = failures(::StoredConfidenceReliability)
        assertTrue(failed.isEmpty(), "Failed checks:\n${failed.joinToString("\n")}")
    }

    @Test
    fun `the calibrated confidence rule meets the contract`() {
        val failed = failures(::CalibratedConfidenceReliability)
        assertTrue(failed.isEmpty(), "Failed checks:\n${failed.joinToString("\n")}")
    }

    @Test
    fun `the stored rule keeps the guess of slices 1 and 2 at 0_5`() {
        val config = TransitionConfig()

        assertEquals(0.5f, config.minConfidence)
        assertFalse(StoredConfidenceReliability().isReliable(transitionProfile(confidence = 0.49f), config))
        assertTrue(StoredConfidenceReliability().isReliable(transitionProfile(confidence = 0.5f), config))
    }

    @Test
    fun `the calibrated rule is on the boundary at 0_15`() {
        val config = TransitionConfig()

        assertEquals(0.15f, config.calibratedMinConfidence)
        assertFalse(CalibratedConfidenceReliability().isReliable(transitionProfile(confidence = 0.149f), config))
        assertTrue(CalibratedConfidenceReliability().isReliable(transitionProfile(confidence = 0.15f), config))
    }
}
