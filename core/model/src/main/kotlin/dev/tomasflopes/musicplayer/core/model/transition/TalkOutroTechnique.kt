package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.log10

/**
 * The next song's beat enters under the talk at the end of the outgoing song (design phase 7, slice 4, ADR 0104).
 *
 * Applies when [TransitionConfig.talkEnabled] is on (ADR 0106), [TalkDetector] says the
 * outgoing tail (outro start to music end) is talk, and both songs have a usable profile. The incoming song starts at
 * [TransitionConfig.talkLeadMs] after the outro start, at speed 1.0 (there is no beat to match), on the first bar of
 * its own grid, and rises over one beat to a flat held level ([TransitionConfig.talkHeldDb], lowered further by the
 * loudness match when the incoming body is louder than the outgoing one, ADR 0107). The outgoing song ducks by
 * [TransitionConfig.talkDuckDb] over one bar. Only over the last [TransitionConfig.talkFadeBars] bars of the overlap
 * does the incoming song rise to full level while the outgoing one falls to nothing. The overlap ends at the music
 * end or after [TransitionConfig.maxOverlapMs], whichever comes first. No bass swap, no ease-back.
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
                    heldDb = config.talkHeldDb + matchDb(talk, incoming, incomingDurationMs, config),
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

    /**
     * The loudness match of ADR 0107, in dB and never above 0: the body of the incoming song against the body of the
     * outgoing one, from the stored energy curves (RMS, so 20 log10). 0 when the switch is off or a curve cannot say.
     */
    private fun matchDb(
        talk: TalkMeasure,
        incoming: TransitionProfile,
        incomingDurationMs: Long,
        config: TransitionConfig,
    ): Float {
        val incomingBody = TalkDetector.measure(incoming, incomingDurationMs)?.bodyMean ?: 0f
        val comparable = config.talkLevelMatch && incomingBody > 0f && talk.bodyMean > 0f
        return if (comparable) {
            (DB_PER_DECADE * log10(talk.bodyMean / incomingBody))
                .toFloat()
                .coerceIn(-config.talkMaxMatchDb, 0f)
        } else {
            0f
        }
    }

    companion object {
        private const val DB_PER_DECADE = 20.0
        const val NAME = "talk-outro"
        private const val MIN_OVERLAP_MS = 4_000L
        private const val FALLBACK_ENTRY_MS = 1_000.0
        private const val FALLBACK_BAR_MS = 2_000.0
    }
}
