package dev.tomasflopes.musicplayer.ui.home

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.fakes.FakeHomeSection
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.FakeScanController
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.playback.PlayRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val playback = FakePlaybackController()
    private val scanner = FakeScanController()

    private fun viewModel(
        songs: FakeSongReader,
        sections: Set<HomeSection> = emptySet(),
    ) = HomeViewModel(playback, songs, scanner, sections)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shuffle all sends every song once, shuffled, as shuffle all`() {
        val refs = listOf(SongRef(SongId(1), ArtistId(1)), SongRef(SongId(2), ArtistId(2)))

        viewModel(FakeSongReader(refs)).shuffleAll()

        assertEquals(
            listOf(PlayRequest(refs, 0, shuffle = true, PlaySource.SHUFFLE_ALL, picked = false)),
            playback.requests,
        )
    }

    @Test
    fun `shuffle all does nothing while the library is empty`() {
        viewModel(FakeSongReader()).shuffleAll()

        assertTrue(playback.requests.isEmpty())
    }

    @Test
    fun `rescan asks the scan controller once`() {
        viewModel(FakeSongReader()).rescan()

        assertEquals(1, scanner.scanRequests)
    }

    @Test
    fun `sections come in order, ties broken by id`() {
        val sections = setOf(FakeHomeSection("lists", 10), FakeHomeSection("b", 5), FakeHomeSection("a", 5))

        val shown = viewModel(FakeSongReader(), sections).sections

        assertEquals(listOf("a", "b", "lists"), shown.map { it.id })
    }

    @Test
    fun `a section added to the set shows up with no change to Home`() {
        val before = setOf<HomeSection>(FakeHomeSection("shuffle_all", 0))
        val after = before + FakeHomeSection("discover", 20)

        assertEquals(listOf("shuffle_all"), viewModel(FakeSongReader(), before).sections.map { it.id })
        assertEquals(listOf("shuffle_all", "discover"), viewModel(FakeSongReader(), after).sections.map { it.id })
    }

    @Test
    fun `two sections with the same id are a wiring mistake and fail at once`() {
        val sections = setOf<HomeSection>(FakeHomeSection("x", 1), FakeHomeSection("x", 2))

        assertThrows(IllegalArgumentException::class.java) { viewModel(FakeSongReader(), sections) }
    }
}
