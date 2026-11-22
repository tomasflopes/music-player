package dev.tomasflopes.musicplayer.core.model.transition

import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.Transition

/**
 * Everything a technique may look at. [phaseSix] is what the phase 6 policy decided; it is never a hard cut here,
 * because the hard-cut rules run before any technique (ADR 0092). The profiles are null when a song has none.
 */
class TransitionRequest(
    val transition: Transition,
    val settings: CrossfadeSettings,
    val phaseSix: CrossfadeDecision,
    val outgoing: TransitionProfile?,
    val incoming: TransitionProfile?,
    val config: TransitionConfig,
)

/**
 * One way to join two songs (ADR 0092). The planner asks the registered techniques in [priority] order, lowest first,
 * and the first plan that is not null wins. A technique never looks at another technique, so a new one is a new class
 * and one `@IntoSet` line, never a branch somewhere else.
 */
interface TransitionTechnique {
    val name: String

    /** Lower asks first. The crossfade fallback is [LAST_PRIORITY]. */
    val priority: Int

    /** A plan, or null when this technique does not apply to the pair. Pure: no player, no clock. */
    fun plan(request: TransitionRequest): TransitionPlan?

    companion object {
        const val TALKING_OUTRO_PRIORITY = 10
        const val BEAT_MATCH_PRIORITY = 20
        const val LAST_PRIORITY = 1_000
    }
}

/** The phase 6 crossfade as the last technique: it always answers, with what the phase 6 policy decided. */
class CrossfadeTechnique : TransitionTechnique {
    override val name = "crossfade"
    override val priority = TransitionTechnique.LAST_PRIORITY

    override fun plan(request: TransitionRequest): TransitionPlan = TransitionPlan.of(request.phaseSix)
}
