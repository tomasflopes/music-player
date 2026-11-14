package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig
import dev.tomasflopes.musicplayer.core.taste.score.SongScore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TopHundredProviderTest : DynamicListProviderContract() {
    private fun scored(
        id: Long,
        score: Double,
    ) = SongScore(SongId(id), score, lastPlayedAtEpochMs = 1)

    /** Ids 1..[count], best first: song 1 has the highest score. */
    private fun ranked(count: Int) = (1..count).map { scored(it.toLong(), (count - it + 1).toDouble()) }

    private fun library(ids: IntRange) = FakeSongReader(ids.map { song(it.toLong(), 0) })

    private fun providerOf(
        scores: List<SongScore>,
        songs: FakeSongReader = library(1..scores.size.coerceAtLeast(1)),
    ) = TopHundredProvider(FakeScoreBoard(scores), songs, TasteConfig())

    private suspend fun DynamicListProvider.ids() = songs().first().map { it.id.value }

    override fun provider(): DynamicListProvider = providerOf(ranked(3))

    @Test
    fun `identity on Home`() {
        val provider = providerOf(ranked(3))

        assertEquals("top_100", provider.id)
        assertEquals("Top 100", provider.title)
        assertEquals(10, provider.order)
        assertEquals(PlaySource.TOP100, provider.playSource)
    }

    @Test
    fun `order follows the score`() =
        runTest {
            val scores = listOf(scored(3, 3.0), scored(1, 2.0), scored(2, 1.0))

            assertEquals(listOf(3L, 1L, 2L), providerOf(scores).ids())
        }

    @Test
    fun `only scores above zero are listed`() =
        runTest {
            val scores = listOf(scored(1, 1.0), scored(2, 0.0), scored(3, -2.0))

            assertEquals(listOf(1L), providerOf(scores, library(1..3)).ids())
        }

    @Test
    fun `at most 100 songs`() =
        runTest {
            assertEquals((1L..100L).toList(), providerOf(ranked(150)).ids())
        }

    @Test
    fun `songs that left the library are skipped and the list is refilled`() =
        runTest {
            val inLibrary = library(11..120)

            assertEquals((11L..110L).toList(), providerOf(ranked(120), inLibrary).ids())
        }

    @Test
    fun `an empty log gives an empty list`() =
        runTest {
            assertEquals(emptyList<Long>(), providerOf(emptyList(), library(1..3)).ids())
        }
}
