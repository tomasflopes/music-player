package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Lines the next song's beat up with the outgoing one on a bar (design phase 7, slice 1: no stretch).
 *
 * Applies when both songs have a usable profile, both tempo confidences reach [TransitionConfig.minConfidence] and
 * the tempos are within [TransitionConfig.unstretchedTolerance] of each other, so the incoming song stays at speed 1.0
 * (slice 2 replaces the tolerance by the stretch limit and adds half and double tempo). The overlap is
 * [TransitionConfig.overlapBars] bars of the outgoing tempo, shrunk bar by bar to fit the cap, the incoming intro and
 * the outgoing tail, and never under [TransitionConfig.minOverlapBars] (then the technique does not apply). It starts
 * on the latest phrase start of the outgoing song, else the latest bar start, that lies in the tail; when nothing
 * lies in the tail it takes the latest bar start whose overlap still ends by the music end.
 */
class BeatMatchTechnique : TransitionTechnique {
    override val name = "beat-match"
    override val priority = TransitionTechnique.BEAT_MATCH_PRIORITY

    override fun plan(request: TransitionRequest): TransitionPlan? {
        val config = request.config
        val outgoing = request.outgoing?.takeIf { it.isUsable && it.tempoConfidence >= config.minConfidence }
        val incoming = request.incoming?.takeIf { it.isUsable && it.tempoConfidence >= config.minConfidence }
        if (outgoing == null || incoming == null) return null
        val ratio = outgoing.tempoBpm / incoming.tempoBpm
        return if (abs(ratio - 1.0) > config.unstretchedTolerance) null else Search(
            outgoingGrid = BeatGrid.of(outgoing, config),
            incomingGrid = BeatGrid.of(incoming, config),
            outgoing = outgoing,
            incoming = incoming,
            outgoingDurationMs = request.transition.outgoing.durationMs,
            ratio = ratio,
            config = config,
        ).plan()
    }

    private class Search(
        val outgoingGrid: BeatGrid,
        val incomingGrid: BeatGrid,
        val outgoing: TransitionProfile,
        val incoming: TransitionProfile,
        val outgoingDurationMs: Long,
        val ratio: Double,
        val config: TransitionConfig,
    ) {
        private val incomingStart = incomingGrid.barStartAtOrAfter(incoming.musicStartMs.toDouble())
        private val musicEnd = minOf(outgoing.musicEndMs, outgoingDurationMs).toDouble()

        /** The longest overlap that fits first; the strict search (inside the tail) comes before the relaxed one. */
        fun plan(): TransitionPlan? = search(strict = true) ?: search(strict = false)

        private fun search(strict: Boolean): TransitionPlan? =
            overlapsLongestFirst().firstNotNullOfOrNull { overlap ->
                switchPoint(overlap, strict)?.let { build(it, overlap) }
            }

        private fun overlapsLongestFirst(): Sequence<Double> =
            (config.overlapBars downTo config.minOverlapBars)
                .asSequence()
                .map { it * outgoingGrid.barMs }
                .filter { it <= config.maxOverlapMs && it <= incoming.introEndMs - incomingStart }

        private fun switchPoint(
            overlapMs: Double,
            strict: Boolean,
        ): Double? {
            val high = musicEnd - overlapMs
            val low = if (strict) outgoing.outroStartMs.toDouble() else 0.0
            val phrase = if (strict) outgoingGrid.latestPhraseStart(low, high) else null
            return phrase ?: outgoingGrid.latestBarStart(low, high)
        }

        private fun build(
            switchPointMs: Double,
            overlapMs: Double,
        ) = TransitionPlan.Overlap(
            technique = NAME,
            switchPointMs = switchPointMs.roundToLong(),
            incomingStartMs = incomingStart.roundToLong(),
            overlapMs = overlapMs.roundToLong(),
            curve = DjVolumeCurve,
            ratio = ratio,
        )
    }

    private companion object {
        const val NAME = "beat-match"
    }
}
