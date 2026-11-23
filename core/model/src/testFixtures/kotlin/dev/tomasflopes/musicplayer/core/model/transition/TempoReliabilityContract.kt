package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** What every [TempoReliability] has to hold to (ADR 0098); plain code, like [TransitionTechniqueContract]. */
object TempoReliabilityContract {
    fun checks(factory: () -> TempoReliability): List<TechniqueCheck> {
        val config = TransitionConfig()
        return listOf(
            TechniqueCheck("a confidence of 1 is reliable") {
                assertTrue(factory().isReliable(transitionProfile(confidence = 1f), config))
            },
            TechniqueCheck("a confidence of 0 is not reliable") {
                assertFalse(factory().isReliable(transitionProfile(confidence = 0f), config))
            },
            TechniqueCheck("a NaN confidence is not reliable") {
                assertFalse(factory().isReliable(transitionProfile(confidence = Float.NaN), config))
            },
            TechniqueCheck("a higher confidence never makes a song less reliable") {
                val rule = factory()
                val answers = (0..100).map { rule.isReliable(transitionProfile(confidence = it / 100f), config) }
                assertEquals(answers.sorted(), answers, "reliable at some confidence, not at a higher one")
            },
            TechniqueCheck("the verdict reads the confidence, not the tempo") {
                val rule = factory()
                val tempos = listOf(60.0, 90.0, 175.0)
                val verdicts =
                    tempos.map { rule.isReliable(transitionProfile(tempo = it, confidence = 0.3f), config) }.toSet()
                assertEquals(1, verdicts.size, "tempo changed the verdict")
            },
        )
    }
}
