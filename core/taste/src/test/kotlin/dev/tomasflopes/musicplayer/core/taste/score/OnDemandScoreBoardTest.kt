package dev.tomasflopes.musicplayer.core.taste.score

import app.cash.turbine.test
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.EmptyCoroutineContext

class OnDemandScoreBoardTest : ScoreBoardContract() {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val source = FakePlayEventSource(listOf(completedPlay(1, now - day), completedPlay(2, now - 2 * day)))

    private fun boardOver(log: FakePlayEventSource) =
        OnDemandScoreBoard(log, TasteScorer(), TasteConfig(), clock, EmptyCoroutineContext)

    override fun board(): ScoreBoard = boardOver(source)

    @Test
    fun `asks the source for the horizon window`() =
        runTest {
            board().observeRanked().first()

            assertEquals(listOf(now - 240 * day), source.requestedSince)
        }

    @Test
    fun `an empty log emits an empty list`() =
        runTest {
            assertTrue(boardOver(FakePlayEventSource()).observeRanked().first().isEmpty())
        }

    @Test
    fun `emits again when the log grows`() =
        runTest {
            board().observeRanked().test {
                assertEquals(2, awaitItem().size)

                source.append(completedPlay(3, now))

                assertEquals(SongId(3), awaitItem().first().songId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an unchanged result is not emitted twice`() =
        runTest {
            board().observeRanked().test {
                awaitItem()

                source.append(completedPlay(3, now - 300 * day))

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
