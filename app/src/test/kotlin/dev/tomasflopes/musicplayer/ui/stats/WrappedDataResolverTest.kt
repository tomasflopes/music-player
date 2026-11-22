package dev.tomasflopes.musicplayer.ui.stats

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.stats.AlbumPlays
import dev.tomasflopes.musicplayer.core.model.stats.ArtistPlays
import dev.tomasflopes.musicplayer.core.model.stats.FirstPlay
import dev.tomasflopes.musicplayer.core.model.stats.SongPlays
import dev.tomasflopes.musicplayer.core.taste.stats.Comeback
import dev.tomasflopes.musicplayer.core.taste.stats.NewDiscoveries
import dev.tomasflopes.musicplayer.core.taste.stats.PeriodStats
import dev.tomasflopes.musicplayer.core.taste.stats.SongSkips
import dev.tomasflopes.musicplayer.core.taste.stats.StatsPeriod
import dev.tomasflopes.musicplayer.fakes.FakeAlbumReader
import dev.tomasflopes.musicplayer.fakes.FakeArtistReader
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testAlbum
import dev.tomasflopes.musicplayer.fakes.testArtist
import dev.tomasflopes.musicplayer.fakes.testSong
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.YearMonth

class WrappedDataResolverTest {
    private val period = StatsPeriod.Month(YearMonth.of(2026, 11))
    private val resolver =
        WrappedDataResolver(
            FakeSongReader(
                songs = listOf(testSong(1, title = "One"), testSong(2, title = "Two"), testSong(9, title = "Cover")),
            ),
            FakeArtistReader(listOf(testArtist(10, "Ten"), testArtist(11, "Eleven"))),
            FakeAlbumReader(listOf(testAlbum(100, "Hundred", "Ten", coverSongId = 9))),
        )

    private fun stats(
        topSongs: List<SongPlays> = emptyList(),
        topArtists: List<ArtistPlays> = emptyList(),
        topAlbums: List<AlbumPlays> = emptyList(),
        mostSkipped: List<SongSkips> = emptyList(),
        first: List<FirstPlay> = emptyList(),
        comeback: Comeback? = null,
    ) = PeriodStats.empty(period).copy(
        totalPlays = 1,
        topSongs = topSongs,
        topArtists = topArtists,
        topAlbums = topAlbums,
        mostSkipped = mostSkipped,
        newDiscoveries = NewDiscoveries(first.size, first),
        comeback = comeback,
    )

    @Test
    fun `songs keep their rank and get their titles`() =
        runTest {
            val data = resolver.resolve(stats(topSongs = listOf(SongPlays(SongId(2), 7), SongPlays(SongId(1), 3))))

            assertEquals(listOf("Two" to 7, "One" to 3), data.topSongs.map { it.song.title to it.count })
        }

    @Test
    fun `a song that left the library is dropped, the others keep their order`() =
        runTest {
            val data = resolver.resolve(stats(topSongs = listOf(SongPlays(SongId(5), 9), SongPlays(SongId(1), 3))))

            assertEquals(listOf(SongId(1)), data.topSongs.map { it.song.id })
        }

    @Test
    fun `artists and albums get names and the album its cover song`() =
        runTest {
            val data =
                resolver.resolve(
                    stats(
                        topArtists = listOf(ArtistPlays(ArtistId(11), 4)),
                        topAlbums = listOf(AlbumPlays(AlbumId(100), 6)),
                    ),
                )

            assertEquals(listOf("Eleven" to 4), data.topArtists.map { it.name to it.count })
            assertEquals(
                "Hundred",
                data.topAlbums
                    .single()
                    .album.title,
            )
            assertEquals(
                SongId(9),
                data.topAlbums
                    .single()
                    .cover
                    ?.id,
            )
        }

    @Test
    fun `skipped songs and discoveries are resolved like songs`() =
        runTest {
            val data =
                resolver.resolve(
                    stats(mostSkipped = listOf(SongSkips(SongId(1), 4)), first = listOf(FirstPlay(SongId(2), 5))),
                )

            assertEquals(listOf("One" to 4), data.mostSkipped.map { it.song.title to it.count })
            assertEquals(listOf("Two"), data.discoveries.map { it.title })
        }

    @Test
    fun `the comeback artist gets a name and keeps its plays`() =
        runTest {
            val data = resolver.resolve(stats(comeback = Comeback(ArtistId(10), plays = 9, previousPlays = 2)))

            assertEquals("Ten", data.comeback?.artist?.name)
            assertEquals(9, data.comeback?.artist?.count)
            assertEquals(2, data.comeback?.previousPlays)
        }

    @Test
    fun `a comeback artist that is not in the library is dropped`() =
        runTest {
            assertNull(
                resolver.resolve(stats(comeback = Comeback(ArtistId(77), plays = 9, previousPlays = 2))).comeback,
            )
        }

    @Test
    fun `an empty period resolves to empty data`() =
        runTest {
            val data = resolver.resolve(PeriodStats.empty(period))

            assertEquals(true, data.isEmpty)
            assertEquals(emptyList<Any>(), data.topSongs + data.topArtists + data.topAlbums + data.mostSkipped)
        }
}
