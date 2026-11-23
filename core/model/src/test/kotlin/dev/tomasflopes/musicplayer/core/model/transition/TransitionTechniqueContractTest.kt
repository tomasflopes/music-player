package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every registered [TransitionTechnique] meets the contract (ADR 0092); a new technique adds one line here. */
class TransitionTechniqueContractTest {
    private fun failures(factory: () -> TransitionTechnique) =
        TransitionTechniqueContract
            .checks(factory)
            .mapNotNull { check ->
                runCatching { check.run() }.exceptionOrNull()?.let { "${check.name}: ${it.message}" }
            }

    @Test
    fun `the beat match meets the contract`() {
        val failed = failures(::BeatMatchTechnique)
        assertTrue(failed.isEmpty(), "Failed checks:\n${failed.joinToString("\n")}")
    }

    @Test
    fun `the talking outro meets the contract`() {
        val failed = failures(::TalkOutroTechnique)
        assertTrue(failed.isEmpty(), "Failed checks:\n${failed.joinToString("\n")}")
    }

    @Test
    fun `the crossfade fallback meets the contract`() {
        val failed = failures(::CrossfadeTechnique)
        assertTrue(failed.isEmpty(), "Failed checks:\n${failed.joinToString("\n")}")
    }

    @Test
    fun `the crossfade fallback always answers, with the phase 6 decision`() {
        val plan = CrossfadeTechnique().plan(transitionRequest(outgoing = null, incoming = null))

        assertEquals(TransitionPlan.Fade(4_000), plan)
    }

    @Test
    fun `the crossfade fallback is the last technique`() {
        assertEquals(TransitionTechnique.LAST_PRIORITY, CrossfadeTechnique().priority)
        assertTrue(BeatMatchTechnique().priority < CrossfadeTechnique().priority)
        assertTrue(TalkOutroTechnique().priority < CrossfadeTechnique().priority)
    }

    @Test
    fun `the talking outro is asked before the beat match`() {
        assertTrue(TalkOutroTechnique().priority < BeatMatchTechnique().priority)
    }
}
