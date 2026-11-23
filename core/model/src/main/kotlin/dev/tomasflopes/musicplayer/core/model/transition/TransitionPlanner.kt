package dev.tomasflopes.musicplayer.core.model.transition

import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadePolicy
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.Transition
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle

/**
 * Chooses how one transition is made (design phase 7, ADR 0092). Pure: no player, no clock, so it runs off the main
 * thread at preload.
 *
 * The phase 6 policy decides first. With the Crossfade style, or when the policy says hard cut (off, repeat one, not
 * the natural end, same song, album in order, unknown duration), the answer is that decision and no technique is
 * asked, which keeps phase 6 exactly as it was. With the DJ mix style the [techniques] are asked in priority order and
 * the first plan that passes [usable] wins; if none does, the phase 6 decision stands. So the result is never worse
 * than a crossfade.
 */
class TransitionPlanner(
    private val techniques: Collection<TransitionTechnique>,
    private val config: TransitionConfig = TransitionConfig(),
    /** The debug counter of the picks (ADR 0112); null counts nothing, as in tests and in the lab. */
    private val tally: TransitionTally? = null,
) {
    private val ordered = techniques.sortedWith(compareBy<TransitionTechnique>({ it.priority }, { it.name }))

    /** The same techniques with other numbers and no tally: the lab plans with its sliders this way (ADR 0113). */
    fun withConfig(config: TransitionConfig) = TransitionPlanner(techniques, config)

    fun plan(
        settings: CrossfadeSettings,
        transition: Transition,
        outgoing: TransitionProfile?,
        incoming: TransitionProfile?,
    ): TransitionPlan {
        val decision = CrossfadePolicy.decide(settings, transition)
        if (settings.style != TransitionStyle.DJ_MIX || decision !is CrossfadeDecision.Fade) {
            return TransitionPlan.of(decision)
        }
        val request = TransitionRequest(transition, settings, decision, outgoing, incoming, config)
        val chosen =
            ordered.firstNotNullOfOrNull { technique ->
                technique.plan(request)?.takeIf { usable(it, transition) }
            } ?: TransitionPlan.of(decision)
        tally?.record(chosen.technique, if (chosen is TransitionPlan.Overlap) null else whyNot(request))
        return chosen
    }

    /** The first reason a technique gives for not applying, in priority order; null when none gives one. */
    private fun whyNot(request: TransitionRequest): FallThrough? =
        ordered.firstNotNullOfOrNull { technique -> technique.whyNot(request) }

    /** A plan with a number out of range, or that does not fit the two songs, is not used. */
    private fun usable(
        plan: TransitionPlan,
        transition: Transition,
    ): Boolean =
        when (plan) {
            TransitionPlan.HardCut -> true
            is TransitionPlan.Fade -> plan.durationMs > 0
            is TransitionPlan.Overlap -> plan.fits(transition.outgoing.durationMs, transition.incoming.durationMs)
        }

    private fun TransitionPlan.Overlap.fits(
        outgoingDurationMs: Long,
        incomingDurationMs: Long,
    ): Boolean =
        overlapMs > 0 &&
            switchPointMs >= 0 &&
            incomingStartMs >= 0 &&
            switchPointMs + overlapMs <= outgoingDurationMs &&
            incomingStartMs + incomingSpanMs <= incomingDurationMs &&
            incomingSpeed.isFinite() &&
            incomingSpeed > 0f
}
