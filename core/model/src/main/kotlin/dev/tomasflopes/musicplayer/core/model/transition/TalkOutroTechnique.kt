package dev.tomasflopes.musicplayer.core.model.transition

/**
 * The next song's beat enters under the talk at the end of the outgoing song (design phase 7, slice 4, ADR 0104).
 *
 * Applies when [TalkDetector] says the outgoing tail (outro start to music end) is talk, both songs have a usable
 * profile and [TransitionConfig.talkEnabled] is on. The incoming song starts at [TransitionConfig.talkLeadMs] after the
 * outro start, at speed 1.0 (there is no beat to match), on the first bar of its own grid, and rises over one beat.
 * The outgoing song ducks by [TransitionConfig.talkDuckDb] over one bar and falls to nothing over the last
 * [TransitionConfig.talkFadeBars] bars of the overlap, which ends at the music end or after
 * [TransitionConfig.maxOverlapMs], whichever comes first. No bass swap, no ease-back.
 *
 * When [reliability] does not trust the incoming tempo the song starts at its music start, rises over a fixed second
 * and the duck and fade use a fixed bar: nothing claims an alignment that is not there. Anything that does not fit
 * gives null, so the beat match and then the crossfade are asked.
 */
class TalkOutroTechnique(
    private val reliability: TempoReliability = CalibratedConfidenceReliability(),
) : TransitionTechnique {
    override val name = NAME
    override val priority = TransitionTechnique.TALKING_OUTRO_PRIORITY

    override fun plan(request: TransitionRequest): TransitionPlan? {
        val config = request.config
        val outgoing = request.outgoing
        val incoming = request.incoming?.takeIf { it.isUsable }
        val talk =
            if (config.talkEnabled && outgoing != null) {
                TalkDetector.detect(outgoing, request.transition.outgoing.durationMs, config)
            } else {
                null
            }
        return if (talk == null || incoming == null) {
            null
        } else {
            build(talk, incoming, request.transition.incoming.durationMs, config)
        }
    }

    private fun build(
        talk: TalkMeasure,
        incoming: TransitionProfile,
        incomingDurationMs: Long,
        config: TransitionConfig,
    ): TransitionPlan? {
        val grid = if (reliability.isReliable(incoming, config)) BeatGrid.of(incoming, config) else null
        val start = grid?.barStartAtOrAfter(incoming.musicStartMs.toDouble())?.toLong() ?: incoming.musicStartMs
        val switchPoint = talk.outroStartMs + config.talkLeadMs
        val overlap = minOf(talk.endMs - switchPoint, config.maxOverlapMs, incomingDurationMs - start)
        val curve =
            if (overlap >= MIN_OVERLAP_MS) {
                TalkCurve.of(
                    overlapMs = overlap,
                    beatMs = grid?.beatMs ?: FALLBACK_ENTRY_MS,
                    barMs = grid?.barMs ?: FALLBACK_BAR_MS,
                    fadeBars = config.talkFadeBars,
                    duckDb = config.talkDuckDb,
                )
            } else {
                null
            }
        return curve?.let {
            TransitionPlan.Overlap(
                technique = NAME,
                switchPointMs = switchPoint,
                incomingStartMs = start,
                overlapMs = overlap,
                curve = it,
            )
        }
    }

    companion object {
        const val NAME = "talk-outro"
        private const val MIN_OVERLAP_MS = 4_000L
        private const val FALLBACK_ENTRY_MS = 1_000.0
        private const val FALLBACK_BAR_MS = 2_000.0
    }
}
