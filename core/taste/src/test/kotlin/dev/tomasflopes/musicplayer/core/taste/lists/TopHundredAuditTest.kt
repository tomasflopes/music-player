package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.score.SongScore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Audit 2026-11-14, S12: a score that is zero up to floating point rounding does not make the list. */
class TopHundredAuditTest {
    @Test
    fun `S12 a score of 1e-16 is not listed`() =
        runTest {
            val scores = listOf(SongScore(SongId(1), 1.0, 1), SongScore(SongId(2), 1e-16, 1))
            val library = FakeSongReader(listOf(song(1, 0), song(2, 0)))
            val provider = TopHundredProvider(FakeScoreBoard(scores), library, TasteConfig())

            assertEquals(listOf(SongId(1)), provider.songs().first().map { it.id })
        }
}
