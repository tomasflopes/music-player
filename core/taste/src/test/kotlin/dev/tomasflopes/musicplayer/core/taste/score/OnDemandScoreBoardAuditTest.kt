package dev.tomasflopes.musicplayer.core.taste.score

import app.cash.turbine.test
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.EmptyCoroutineContext

/** Audit 2026-11-14, S7: rows that fall out of the horizon while the board is being collected stop counting. */
class OnDemandScoreBoardAuditTest {
    private val day = 86_400_000L

    private class MovingClock(
        var nowMs: Long,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    }

    @Test
    fun `S7 a row that ages past the horizon during collection is dropped at the next emission`() =
        runTest {
            val clock = MovingClock(1_000 * day)
            val source =
                FakePlayEventSource(
                    listOf(completedPlay(1, clock.nowMs - 239 * day), completedPlay(2, clock.nowMs - day)),
                )
            val board = OnDemandScoreBoard(source, TasteScorer(), TasteConfig(), clock, EmptyCoroutineContext)

            board.observeRanked().test {
                assertEquals(setOf(SongId(1), SongId(2)), awaitItem().map { it.songId }.toSet())

                clock.nowMs += 5 * day
                source.append(completedPlay(3, clock.nowMs))

                assertEquals(setOf(SongId(2), SongId(3)), awaitItem().map { it.songId }.toSet())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
