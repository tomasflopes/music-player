package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.taste.config.SignalWeights

/**
 * The signed weight of one log row (design §7.4), before decay and burst softening. A row's weight is a start
 * bonus (the owner chose the song) plus an end weight (how the play went).
 */
class SignalRules(
    private val weights: SignalWeights = SignalWeights(),
) {
    fun outcomeOf(event: PlayEvent): PlayOutcome {
        val fraction = fractionListened(event)
        return when {
            event.endedBy == EndedBy.COMPLETED -> PlayOutcome.COMPLETED
            fraction != null && fraction > weights.completedFraction -> PlayOutcome.COMPLETED
            event.endedBy == EndedBy.STOPPED -> PlayOutcome.NEUTRAL
            event.listenedMs < weights.earlySkipMs -> PlayOutcome.SKIPPED_EARLY
            fraction != null && fraction < weights.midSkipFraction -> PlayOutcome.SKIPPED_MID
            else -> PlayOutcome.SKIPPED_LATE
        }
    }

    fun weightOf(
        event: PlayEvent,
        outcome: PlayOutcome,
    ): Double = startBonus(event, outcome) + endWeight(event, outcome)

    /** Null when the duration is unknown: rows written before the log read it from the library have none. */
    private fun fractionListened(event: PlayEvent): Double? =
        if (event.songDurationMs <= 0) null else event.listenedMs.toDouble() / event.songDurationMs

    private fun startBonus(
        event: PlayEvent,
        outcome: PlayOutcome,
    ): Double = if (event.startedBy == StartedBy.PICKED && outcome != PlayOutcome.SKIPPED_EARLY) weights.picked else 0.0

    private fun endWeight(
        event: PlayEvent,
        outcome: PlayOutcome,
    ): Double =
        when (outcome) {
            PlayOutcome.COMPLETED -> if (completionCounts(event)) weights.completed else 0.0
            PlayOutcome.SKIPPED_EARLY -> weights.skipEarly
            PlayOutcome.SKIPPED_MID -> weights.skipMid
            PlayOutcome.SKIPPED_LATE -> weights.skipLate
            PlayOutcome.NEUTRAL -> 0.0
        }

    private fun completionCounts(event: PlayEvent): Boolean = event.shuffle || event.source in COMPLETION_COUNTS_FOR

    private companion object {
        /** Finishing a song is a vote only when the app chose it: under shuffle or from a mix (design §7.4). */
        val COMPLETION_COUNTS_FOR = setOf(PlaySource.MIX, PlaySource.RADIO)
    }
}
