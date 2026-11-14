package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.taste.config.SignalWeights
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SignalRulesTest {
    private val rules = SignalRules(SignalWeights())

    private fun event(
        listenedMs: Long,
        durationMs: Long = 200_000,
        endedBy: EndedBy = EndedBy.SKIPPED,
        startedBy: StartedBy = StartedBy.AUTO,
        shuffle: Boolean = false,
        source: PlaySource = PlaySource.ALBUM,
    ) = PlayEvent(SongId(1), 0, listenedMs, durationMs, endedBy, source, startedBy, shuffle)

    private fun outcome(event: PlayEvent) = rules.outcomeOf(event)

    private fun weight(event: PlayEvent) = rules.weightOf(event, rules.outcomeOf(event))

    @Test
    fun `completed under shuffle is 1`() {
        assertEquals(1.0, weight(event(200_000, endedBy = EndedBy.COMPLETED, shuffle = true)))
    }

    @Test
    fun `completed under mix or radio is 1`() {
        assertEquals(1.0, weight(event(200_000, endedBy = EndedBy.COMPLETED, source = PlaySource.MIX)))
        assertEquals(1.0, weight(event(200_000, endedBy = EndedBy.COMPLETED, source = PlaySource.RADIO)))
    }

    @Test
    fun `completed album track in order is 0`() {
        assertEquals(0.0, weight(event(200_000, endedBy = EndedBy.COMPLETED)))
    }

    @Test
    fun `picked completed under shuffle is 4 and in order is 3`() {
        val picked = event(200_000, endedBy = EndedBy.COMPLETED, startedBy = StartedBy.PICKED)

        assertEquals(4.0, weight(picked.copy(shuffle = true)))
        assertEquals(3.0, weight(picked))
    }

    @Test
    fun `skip at 14999 ms is early and -2`() {
        assertEquals(PlayOutcome.SKIPPED_EARLY, outcome(event(14_999)))
        assertEquals(-2.0, weight(event(14_999)))
    }

    @Test
    fun `skip at 15 s of 200 s is -1`() {
        assertEquals(PlayOutcome.SKIPPED_MID, outcome(event(15_000)))
        assertEquals(-1.0, weight(event(15_000)))
    }

    @Test
    fun `skip at 49 percent is -1 and at 50 percent is 0`() {
        assertEquals(-1.0, weight(event(98_000)))
        assertEquals(PlayOutcome.SKIPPED_LATE, outcome(event(100_000)))
        assertEquals(0.0, weight(event(100_000)))
    }

    @Test
    fun `play past 80 percent counts as completed even when skipped`() {
        assertEquals(PlayOutcome.SKIPPED_LATE, outcome(event(160_000)))
        assertEquals(PlayOutcome.COMPLETED, outcome(event(160_001)))
        assertEquals(1.0, weight(event(160_001, shuffle = true)))
    }

    @Test
    fun `picked then skipped inside 15 s gets no picked bonus`() {
        assertEquals(-2.0, weight(event(5_000, startedBy = StartedBy.PICKED)))
        assertEquals(2.0, weight(event(20_000, startedBy = StartedBy.PICKED)))
    }

    @Test
    fun `stopped under 80 percent is neutral and 90 percent is completed`() {
        assertEquals(PlayOutcome.NEUTRAL, outcome(event(5_000, endedBy = EndedBy.STOPPED)))
        assertEquals(0.0, weight(event(50_000, endedBy = EndedBy.STOPPED)))
        assertEquals(PlayOutcome.COMPLETED, outcome(event(180_000, endedBy = EndedBy.STOPPED)))
    }

    @Test
    fun `unknown duration - completed counts, skipped at 20 s is 0, skipped at 5 s is -2`() {
        assertEquals(PlayOutcome.COMPLETED, outcome(event(0, durationMs = 0, endedBy = EndedBy.COMPLETED)))
        assertEquals(PlayOutcome.SKIPPED_LATE, outcome(event(20_000, durationMs = 0)))
        assertEquals(0.0, weight(event(20_000, durationMs = 0)))
        assertEquals(-2.0, weight(event(5_000, durationMs = 0)))
    }

    @Test
    fun `listened longer than the duration is completed`() {
        assertEquals(PlayOutcome.COMPLETED, outcome(event(250_000)))
    }
}
