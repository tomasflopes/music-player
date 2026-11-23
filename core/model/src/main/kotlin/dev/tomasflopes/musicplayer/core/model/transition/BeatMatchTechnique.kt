package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.roundToLong

/**
 * Lines the next song's beat up with the outgoing one on a bar and stretches it to the outgoing tempo (design phase 7,
 * slices 1 and 2, ADR 0092, 0094).
 *
 * Applies when both songs have a usable profile, [reliability] trusts both stored tempos (ADR 0098) and
 * [TempoMatcher] finds a reading of the incoming tempo (x0.5, x1 or x2) within [TransitionConfig.maxStretch] of the
 * outgoing tempo. The incoming song then plays at speed r (outgoing tempo over the incoming tempo as read) with its
 * pitch kept, so its beats fall on the outgoing beats, and [EaseBack] returns it to 1.0 afterwards.
 *
 * The overlap is [TransitionConfig.overlapBars] bars of the outgoing tempo in real time, shrunk bar by bar to fit the
 * cap, the incoming intro (the incoming song covers r times the overlap of its file) and the outgoing tail, and never
 * under [TransitionConfig.minOverlapBars] (then the technique does not apply). It starts on the latest phrase start of
 * the outgoing song, else the latest bar start, that lies in the tail; when nothing lies in the tail it takes the
 * latest bar start whose overlap still ends by the music end. The incoming song starts on a bar of its own grid at the
 * matched tempo, at or after its music start.
 */
class BeatMatchTechnique(
    private val reliability: TempoReliability = CalibratedConfidenceReliability(),
) : TransitionTechnique {
    override val name = "beat-match"
    override val priority = TransitionTechnique.BEAT_MATCH_PRIORITY

    override fun plan(request: TransitionRequest): TransitionPlan? {
        val config = request.config
        val outgoing = request.outgoing?.takeIf { it.isUsable && reliability.isReliable(it, config) }
        val incoming = request.incoming?.takeIf { it.isUsable && reliability.isReliable(it, config) }
        val match =
            if (outgoing == null || incoming == null) {
                null
            } else {
                TempoMatcher.best(outgoing.tempoBpm, incoming.tempoBpm, config.maxStretch)
            }
        return if (outgoing == null || incoming == null || match == null) {
            null
        } else {
            Search(
                outgoingGrid = BeatGrid.of(outgoing, config),
                incomingGrid = BeatGrid.of(incoming, config, match.multiplier),
                outgoing = outgoing,
                incoming = incoming,
                outgoingDurationMs = request.transition.outgoing.durationMs,
                ratio = match.ratio,
                config = config,
            ).plan()
        }
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

        /** Real-time lengths; the incoming intro has to hold [ratio] times the overlap of its file. */
        private fun overlapsLongestFirst(): Sequence<Double> =
            (config.overlapBars downTo config.minOverlapBars)
                .asSequence()
                .map { it * outgoingGrid.barMs }
                .filter { it <= config.maxOverlapMs && it * ratio <= incoming.introEndMs - incomingStart }

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
            incomingSpeed = ratio.toFloat(),
            easeBack = EaseBack.steps(config, ratio, incomingGrid, incomingStart + overlapMs * ratio),
            outgoingGrid = outgoingGrid,
            incomingGrid = incomingGrid,
        )
    }

    private companion object {
        const val NAME = "beat-match"
    }
}
