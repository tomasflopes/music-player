package dev.tomasflopes.musicplayer.ui.playlists

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.playlists.AddAlbumToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.AddArtistToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.AddSongsToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.CreatePlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.InMemoryPlaylists
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AddToPlaylistViewModelTest {
    private val songs = (1L..6L).map { testSong(it) }
    private val playlists = InMemoryPlaylists(songs)
    private var nowMs = 1_000L
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
        }
    private val albumSongs = listOf(testSong(11, trackNumber = 2), testSong(10, trackNumber = 1))
    private val reader =
        FakeSongReader(
            songs = songs,
            albumSongs = mapOf(AlbumId(7) to albumSongs),
            artistSongs = mapOf(ArtistId(3) to listOf(testSong(20, albumId = null))),
        )
    private val add = AddSongsToPlaylist(playlists, clock)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        AddToPlaylistViewModel(
            playlists,
            CreatePlaylist(playlists, clock),
            add,
            AddAlbumToPlaylist(reader, add),
            AddArtistToPlaylist(reader, add),
        )

    private suspend fun newPlaylist(name: String): PlaylistId {
        val created = CreatePlaylist(playlists, clock)(name) as CreatePlaylist.Result.Created
        return created.id
    }

    private fun ids(vararg values: Long) = values.map(::SongId)

    @Test
    fun `the most recently used playlist comes first`() =
        runTest {
            val viewModel = viewModel()
            val old = newPlaylist("Old")
            nowMs = 2_000
            newPlaylist("New")
            nowMs = 3_000
            add(old, ids(1))

            val names = viewModel.playlists.first { it.size == 2 }.map { it.name }

            assertEquals(listOf("Old", "New"), names)
        }

    @Test
    fun `adding one song to a playlist says so and stores it`() =
        runTest {
            val id = newPlaylist("Gym")
            val viewModel = viewModel()
            val gym = viewModel.playlists.first { it.isNotEmpty() }.single()

            val message = viewModel.add(AddTarget.Songs(ids(4)), gym)

            assertEquals("Added to Gym", message)
            assertEquals(ids(4), playlists.storedSongs(id))
        }

    @Test
    fun `adding a song that is already there changes nothing but still marks the playlist as used`() =
        runTest {
            val id = newPlaylist("Gym")
            add(id, ids(4))
            nowMs = 9_000
            val viewModel = viewModel()
            val gym = viewModel.playlists.first { it.isNotEmpty() }.single()

            val message = viewModel.add(AddTarget.Songs(ids(4)), gym)

            assertEquals("Already in Gym", message)
            assertEquals(ids(4), playlists.storedSongs(id))
            assertEquals(9_000L, playlists.lastUsedOf(id))
        }

    @Test
    fun `adding many reports what went in and what was there`() =
        runTest {
            val id = newPlaylist("Gym")
            add(id, ids(1, 2))
            val viewModel = viewModel()
            val gym = viewModel.playlists.first { it.isNotEmpty() }.single()

            val message = viewModel.add(AddTarget.Songs(ids(2, 3, 4)), gym)

            assertEquals("Added 2, 1 already there", message)
            assertEquals(ids(1, 2, 3, 4), playlists.storedSongs(id))
        }

    @Test
    fun `a whole album goes in in track order`() =
        runTest {
            val id = newPlaylist("Gym")
            val viewModel = viewModel()
            val gym = viewModel.playlists.first { it.isNotEmpty() }.single()

            val message = viewModel.add(AddTarget.Album(AlbumId(7)), gym)

            assertEquals("Added 2 songs to Gym", message)
            assertEquals(ids(10, 11), playlists.storedSongs(id))
        }

    @Test
    fun `a whole artist goes in`() =
        runTest {
            val id = newPlaylist("Gym")
            val viewModel = viewModel()
            val gym = viewModel.playlists.first { it.isNotEmpty() }.single()

            viewModel.add(AddTarget.Artist(ArtistId(3)), gym)

            assertEquals(ids(20), playlists.storedSongs(id))
        }

    @Test
    fun `a new playlist is created with the songs`() =
        runTest {
            val viewModel = viewModel()

            val message = viewModel.createAndAdd(AddTarget.Songs(ids(5, 6)), "  Run ")

            assertEquals("Added 2 songs to Run", message)
            val created = viewModel.playlists.first { it.isNotEmpty() }.single()
            assertEquals("Run", created.name)
            assertEquals(ids(5, 6), playlists.storedSongs(created.id))
        }

    @Test
    fun `a blank name creates nothing`() =
        runTest {
            val viewModel = viewModel()

            assertNull(viewModel.createAndAdd(AddTarget.Songs(ids(5)), "  "))
            assertEquals(emptyList<Any>(), playlists.observePlaylists().first())
        }
}
