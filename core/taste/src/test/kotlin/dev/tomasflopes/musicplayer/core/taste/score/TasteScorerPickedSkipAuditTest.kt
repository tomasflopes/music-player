package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Audit 2026-11-14, S6: a completed play clears the -1 of a picked song that was skipped mid-way. */
class TasteScorerPickedSkipAuditTest {
    private val scorer = TasteScorer()
    private val now = 1_000 * 86_400_000L

    /** Picked (+3), then skipped at 25 % (-1). */
    private val pickedMidSkip =
        PlayEvent(SongId(1), now, 50_000, 200_000, EndedBy.SKIPPED, PlaySource.ALBUM, StartedBy.PICKED, false)

    private val completedUnderShuffle =
        PlayEvent(SongId(1), now, 200_000, 200_000, EndedBy.COMPLETED, PlaySource.SHUFFLE_ALL, StartedBy.AUTO, true)

    private fun scoreOf(events: List<PlayEvent>) = scorer.score(events, now).single().score

    @Test
    fun `S6 a picked mid skip on its own is 2`() {
        assertEquals(2.0, scoreOf(listOf(pickedMidSkip)))
    }

    @Test
    fun `S6 a later completed play clears the skip but keeps the pick bonus`() {
        assertEquals(4.0, scoreOf(listOf(pickedMidSkip, completedUnderShuffle)))
    }
}
