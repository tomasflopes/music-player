package dev.tomasflopes.musicplayer.core.model.transition

/**
 * Decides whether the stored tempo of a song can be trusted for a beat match (design phase 7, ADR 0098). It sees one
 * profile and the config, nothing else, so a rule can be swapped without touching [BeatMatchTechnique].
 */
fun interface TempoReliability {
    fun isReliable(
        profile: TransitionProfile,
        config: TransitionConfig,
    ): Boolean
}

/**
 * The rule of slices 1 and 2, kept for comparison (CLAUDE.md, decision points): the stored tempo confidence reaches
 * [TransitionConfig.minConfidence]. On the owner's library that passes 53 of 14,165 songs (ADR 0099).
 */
class StoredConfidenceReliability : TempoReliability {
    override fun isReliable(
        profile: TransitionProfile,
        config: TransitionConfig,
    ) = profile.tempoConfidence >= config.minConfidence
}

/**
 * The rule chosen from the owner's tempo and click checks (ADR 0099): the stored tempo confidence reaches
 * [TransitionConfig.calibratedMinConfidence]. Same signal as [StoredConfidenceReliability], a threshold taken from
 * data instead of a guess.
 */
class CalibratedConfidenceReliability : TempoReliability {
    override fun isReliable(
        profile: TransitionProfile,
        config: TransitionConfig,
    ) = profile.tempoConfidence >= config.calibratedMinConfidence
}
