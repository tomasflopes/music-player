package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.taste.config.SignalWeights
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Audit 2026-11-14, S5: "completed" is strictly more than 80 % of the song (design §7.4). */
class SignalRulesAuditTest {
    private val rules = SignalRules(SignalWeights())

    private fun skippedAfter(listenedMs: Long) =
        PlayEvent(SongId(1), 0, listenedMs, 200_000, EndedBy.SKIPPED, PlaySource.ALBUM, StartedBy.AUTO, false)

    @Test
    fun `S5 exactly 80 percent is not completed`() {
        assertEquals(PlayOutcome.SKIPPED_LATE, rules.outcomeOf(skippedAfter(160_000)))
    }

    @Test
    fun `S5 just over 80 percent is completed`() {
        assertEquals(PlayOutcome.COMPLETED, rules.outcomeOf(skippedAfter(160_001)))
    }
}
