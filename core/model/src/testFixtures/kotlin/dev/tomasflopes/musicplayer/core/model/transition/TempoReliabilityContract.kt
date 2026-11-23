package dev.tomasflopes.musicplayer.core.model.transition

/** What every [TempoReliability] has to hold to (ADR 0098); plain code, like [TransitionTechniqueContract]. */
object TempoReliabilityContract {
    fun checks(factory: () -> TempoReliability): List<TechniqueCheck> {
        val config = TransitionConfig()
        return listOf(
            TechniqueCheck("a confidence of 1 is reliable") {
                check(factory().isReliable(transitionProfile(confidence = 1f), config)) { "not reliable at 1" }
            },
            TechniqueCheck("a confidence of 0 is not reliable") {
                check(!factory().isReliable(transitionProfile(confidence = 0f), config)) { "reliable at 0" }
            },
            TechniqueCheck("a NaN confidence is not reliable") {
                check(!factory().isReliable(transitionProfile(confidence = Float.NaN), config)) { "reliable at NaN" }
            },
            TechniqueCheck("a higher confidence never makes a song less reliable") {
                val rule = factory()
                val answers = (0..100).map { rule.isReliable(transitionProfile(confidence = it / 100f), config) }
                check(answers.sorted() == answers) { "reliable at some confidence, not at a higher one" }
            },
            TechniqueCheck("the verdict reads the confidence, not the tempo") {
                val rule = factory()
                val tempos = listOf(60.0, 90.0, 175.0)
                val verdicts =
                    tempos.map { rule.isReliable(transitionProfile(tempo = it, confidence = 0.3f), config) }.toSet()
                check(verdicts.size == 1) { "tempo changed the verdict" }
            },
        )
    }
}
