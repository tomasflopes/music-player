package dev.tomasflopes.musicplayer.ui.library.songs

import dev.tomasflopes.musicplayer.core.model.Section
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.ui.library.IndexedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongsViewModelTest {
    // In the order the reader delivers them: by sort key, so "The Weeknd" sits under W.
    private val chainz = testSong(1, title = "2 Chainz")
    private val album = testSong(2, title = "Álbum")
    private val beta = testSong(3, title = "Beta")
    private val weeknd = testSong(4, title = "The Weeknd")
    private val zed = testSong(5, title = "Zed")
    private val library = listOf(chainz, album, beta, weeknd, zed)

    private val playback = FakePlaybackController()
    private val reader = FakeSongReader(songs = library)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SongsViewModel(playback, reader)

    private suspend fun SongsViewModel.loaded(): IndexedList<Song> = songs.filterNotNull().first()

    @Test
    fun `sections match the sort keys`() =
        runTest {
            val shown = viewModel().loaded()

            val expected = listOf(Section('#', 0), Section('A', 1), Section('B', 2), Section('W', 3), Section('Z', 4))
            assertEquals(expected, shown.sections)
            assertEquals(library, shown.items)
        }

    @Test
    fun `a library change builds new sections`() =
        runTest {
            val viewModel = viewModel()
            viewModel.loaded()

            reader.library.value = listOf(album, beta)

            val shown = viewModel.songs.filterNotNull().first { it.items.size == 2 }
            assertEquals(listOf(Section('A', 0), Section('B', 1)), shown.sections)
        }

    @Test
    fun `an empty library is an empty list, not a loading one`() =
        runTest {
            reader.library.value = emptyList()

            val shown = viewModel().loaded()

            assertTrue(shown.items.isEmpty())
            assertTrue(shown.sections.isEmpty())
        }

    @Test
    fun `tapping the third song plays the list from index 2, in order, not shuffled`() =
        runTest {
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.onSongClick(2)

            val expected = PlayRequest(library.map { it.ref }, 2, shuffle = false, PlaySource.SONGS, picked = true)
            assertEquals(listOf(expected), playback.requests)
        }

    @Test
    fun `a tap outside the list plays nothing`() =
        runTest {
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.onSongClick(library.size)
            viewModel.onSongClick(-1)

            assertTrue(playback.requests.isEmpty())
        }

    @Test
    fun `a tap before the library is read plays nothing`() {
        // No collector yet, so the state is still the initial null.
        val viewModel = viewModel()

        assertNull(viewModel.songs.value)
        viewModel.onSongClick(0)

        assertTrue(playback.requests.isEmpty())
    }

    @Test
    fun `Play next sends that song's ref and nothing else`() {
        viewModel().onPlayNext(beta)

        assertEquals(listOf(listOf(beta.ref)), playback.playNextCalls)
        assertTrue(playback.addToQueueCalls.isEmpty())
        assertTrue(playback.requests.isEmpty())
    }

    @Test
    fun `Add to queue sends that song's ref and nothing else`() {
        viewModel().onAddToQueue(weeknd)

        assertEquals(listOf(listOf(weeknd.ref)), playback.addToQueueCalls)
        assertTrue(playback.playNextCalls.isEmpty())
        assertTrue(playback.requests.isEmpty())
    }
}
