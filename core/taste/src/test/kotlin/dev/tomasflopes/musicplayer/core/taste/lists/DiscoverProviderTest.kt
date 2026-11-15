package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSongSource
import dev.tomasflopes.musicplayer.core.taste.config.DiscoverConfig
import dev.tomasflopes.musicplayer.core.taste.score.ArtistScore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DiscoverProviderTest : DynamicListProviderContract() {
    private class FakePlayed(
        initial: Set<Long> = emptySet(),
    ) : PlayedSongSource {
        val ids = MutableStateFlow(initial.mapTo(HashSet()) { SongId(it) }.toSet())

        override fun observePlayedIds(): Flow<Set<SongId>> = ids
    }

    private fun soloists(count: Int): List<Song> =
        (1..count).map { song(it.toLong(), 0).copy(artistId = ArtistId(it.toLong())) }

    private fun providerOf(
        library: List<Song> = soloists(5),
        played: FakePlayed = FakePlayed(),
        artists: List<ArtistScore> = emptyList(),
        seed: Long = 1,
    ) = DiscoverProvider(
        FakeSongReader(library),
        played,
        FakeScoreBoard(emptyList(), artists),
        DiscoverPolicy(DiscoverConfig()),
        seed,
    )

    private suspend fun DynamicListProvider.ids() = songs().first().map { it.id.value }

    override fun provider(): DynamicListProvider = providerOf()

    @Test
    fun `identity on Home, next to Top 100`() {
        val provider = providerOf()

        assertEquals("discover", provider.id)
        assertEquals("Discover", provider.title)
        assertEquals(20, provider.order)
        assertEquals(PlaySource.DISCOVER, provider.playSource)
    }

    @Test
    fun `only songs the log has never heard of are listed`() =
        runTest {
            val provider = providerOf(played = FakePlayed(setOf(1, 2)))

            assertEquals(listOf(3L, 4L, 5L), provider.ids().sorted())
        }

    @Test
    fun `a song leaves the list as soon as it is played`() =
        runTest {
            val played = FakePlayed()
            val provider = providerOf(played = played)
            val first = provider.ids()

            played.ids.value = setOf(SongId(first.first()))

            assertEquals(first.drop(1), provider.ids())
        }

    @Test
    fun `a liked artist leads the list`() =
        runTest {
            val artists = listOf(ArtistScore(ArtistId(4), score = 20.0, lastSignalAtEpochMs = 1))

            assertEquals(4L, providerOf(artists = artists).ids().first())
        }

    @Test
    fun `the same seed gives the same list and another seed another one`() =
        runTest {
            val library = soloists(100)

            assertEquals(providerOf(library, seed = 3).ids(), providerOf(library, seed = 3).ids())
            assertNotEquals(providerOf(library, seed = 3).ids(), providerOf(library, seed = 4).ids())
        }

    @Test
    fun `an empty library gives an empty list`() =
        runTest {
            assertEquals(emptyList<Long>(), providerOf(library = emptyList()).ids())
        }
}
