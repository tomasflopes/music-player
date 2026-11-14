package dev.tomasflopes.musicplayer.ui.playlists

import dev.tomasflopes.musicplayer.core.model.SongId
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SongPickerViewModelTest {
    private val songs =
        listOf(
            testSong(1, albumId = null, title = "Álbum Zero", artistName = "Ed"),
            testSong(2, albumId = null, title = "Beta", artistName = "Kendrick Lamar"),
            testSong(3, albumId = null, title = "Gamma", artistName = "Ed"),
        )
    private val store = InMemoryPlaylists(songs)
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)
    private val add = AddSongsToPlaylist(store, clock)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun opened(vararg inside: Long): Pair<SongPickerViewModel, PlaylistId> {
        val id = (CreatePlaylist(store, clock)("Gym", inside.map(::SongId)) as CreatePlaylist.Result.Created).id
        val viewModel = SongPickerViewModel(store, FakeSongReader(songs = songs), add)
        viewModel.open(id)
        return viewModel to id
    }

    @Test
    fun `with no query every song is listed in library order`() =
        runTest {
            val (viewModel, _) = opened()

            assertEquals(songs, viewModel.state.first { it.songs.isNotEmpty() }.songs)
        }

    @Test
    fun `a query matches title or artist ignoring case and accents`() =
        runTest {
            val (viewModel, _) = opened()

            viewModel.onQueryChange("album")
            assertEquals(listOf(songs[0]), viewModel.state.first { it.songs.size == 1 }.songs)

            viewModel.onQueryChange("ED")
            assertEquals(listOf(songs[0], songs[2]), viewModel.state.first { it.songs.size == 2 }.songs)
        }

    @Test
    fun `songs already in the playlist are marked and cannot be selected`() =
        runTest {
            val (viewModel, _) = opened(2)

            viewModel.onToggle(SongId(2))
            viewModel.onToggle(SongId(3))

            val state = viewModel.state.first { it.songs.isNotEmpty() && it.alreadyIn.isNotEmpty() }
            assertEquals(setOf(SongId(2)), state.alreadyIn)
            assertEquals(setOf(SongId(3)), state.selected)
        }

    @Test
    fun `adding puts the selection in library order and clears it`() =
        runTest {
            val (viewModel, id) = opened()
            viewModel.onToggle(SongId(3))
            viewModel.onToggle(SongId(1))

            val message = viewModel.addSelected()

            assertEquals("Added 2 songs to Gym", message)
            assertEquals(listOf(SongId(1), SongId(3)), store.storedSongs(id))
            assertEquals(emptySet<SongId>(), viewModel.state.first().selected)
        }

    @Test
    fun `adding nothing selected does nothing`() =
        runTest {
            val (viewModel, id) = opened()

            assertEquals(null, viewModel.addSelected())
            assertEquals(emptyList<SongId>(), store.storedSongs(id))
        }

    @Test
    fun `the selection survives a new query`() =
        runTest {
            val (viewModel, _) = opened()
            viewModel.onToggle(SongId(2))

            viewModel.onQueryChange("gamma")

            assertEquals(setOf(SongId(2)), viewModel.state.first { it.songs.size == 1 }.selected)
        }
}
