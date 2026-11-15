package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.PlaylistAddSource
import dev.tomasflopes.musicplayer.core.model.ports.SearchEventSource
import dev.tomasflopes.musicplayer.core.taste.score.FakePlayEventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.EmptyCoroutineContext

class RewindProviderTest : DynamicListProviderContract() {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private val cutoff = now - 60 * day

    private class FakePlayedSince(
        initial: Set<Long> = emptySet(),
    ) : PlayedSinceSource {
        val ids = MutableStateFlow(initial.mapTo(HashSet()) { SongId(it) }.toSet())
        val requestedSince = mutableListOf<Long>()

        override fun observePlayedIdsSince(sinceEpochMs: Long): Flow<Set<SongId>> {
            requestedSince += sinceEpochMs
            return ids
        }
    }

    private fun soloists(count: Int): List<Song> =
        (1..count).map { song(it.toLong(), 0).copy(artistId = ArtistId(it.toLong())) }

    private fun lovedThen(
        song: Long,
        days: Long = 1,
    ) = PlayEvent(
        SongId(song),
        cutoff - days * day,
        200_000,
        200_000,
        EndedBy.COMPLETED,
        PlaySource.ALBUM,
        StartedBy.PICKED,
        false,
    )

    private fun providerOf(
        library: List<Song> = soloists(5),
        plays: List<PlayEvent> = listOf(lovedThen(1)),
        lately: FakePlayedSince = FakePlayedSince(),
        events: FakePlayEventSource = FakePlayEventSource(plays),
        seed: Long = 1,
    ) = RewindProvider(
        FakeSongReader(library),
        RewindLogs(
            lately,
            events,
            PlaylistAddSource { flowOf(emptyList()) },
            SearchEventSource { flowOf(emptyList()) },
        ),
        RewindPolicy(),
        Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC),
        seed,
        EmptyCoroutineContext,
    )

    private suspend fun DynamicListProvider.ids() = songs().first().map { it.id.value }

    override fun provider(): DynamicListProvider = providerOf()

    @Test
    fun `identity on Home, after Discover`() {
        val provider = providerOf()

        assertEquals("rewind", provider.id)
        assertEquals("Rewind", provider.title)
        assertEquals(30, provider.order)
        assertEquals(PlaySource.REWIND, provider.playSource)
    }

    @Test
    fun `only songs loved before the quiet window are listed`() =
        runTest {
            val plays = listOf(lovedThen(1), lovedThen(2, days = 45))

            assertEquals(listOf(1L), providerOf(plays = plays).ids())
        }

    @Test
    fun `a song with a play in the quiet window is not listed`() =
        runTest {
            val provider = providerOf(plays = listOf(lovedThen(1), lovedThen(2)), lately = FakePlayedSince(setOf(2)))

            assertEquals(listOf(1L), provider.ids())
        }

    @Test
    fun `a song leaves the list as soon as it is played`() =
        runTest {
            val lately = FakePlayedSince()
            val provider = providerOf(plays = (1L..5L).map { lovedThen(it) }, lately = lately)
            val first = provider.ids()

            lately.ids.value = setOf(SongId(first.first()))

            assertEquals(first.drop(1), provider.ids())
        }

    @Test
    fun `the quiet window and the log horizon come from now`() =
        runTest {
            val lately = FakePlayedSince()
            val events = FakePlayEventSource()

            providerOf(lately = lately, events = events).ids()

            assertEquals(listOf(cutoff), lately.requestedSince)
            assertEquals(listOf(cutoff - 240 * day), events.requestedSince)
        }

    @Test
    fun `the same seed gives the same list and another seed another one`() =
        runTest {
            val library = soloists(100)
            val plays = (1L..100L).map { lovedThen(it) }

            assertEquals(providerOf(library, plays, seed = 3).ids(), providerOf(library, plays, seed = 3).ids())
            assertNotEquals(providerOf(library, plays, seed = 3).ids(), providerOf(library, plays, seed = 4).ids())
        }

    @Test
    fun `an empty log gives an empty list`() =
        runTest {
            assertEquals(emptyList<Long>(), providerOf(plays = emptyList()).ids())
        }
}
