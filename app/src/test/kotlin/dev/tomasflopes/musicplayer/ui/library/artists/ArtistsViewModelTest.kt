package dev.tomasflopes.musicplayer.ui.library.artists

import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Section
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.fakes.FakeArtistReader
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testArtist
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistsViewModelTest {
    private val ed = testArtist(1, "Ed")
    private val kendrick = testArtist(2, "Kendrick Lamar")
    private val weeknd = testArtist(3, "The Weeknd")
    private val library = listOf(ed, kendrick, weeknd)

    private val edSongs = listOf(testSong(10, artistId = 1), testSong(11, artistId = 1))
    private val playback = FakePlaybackController()
    private val songs = FakeSongReader(artistSongs = mapOf(ArtistId(1) to edSongs))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ArtistsViewModel(playback, songs, FakeArtistReader(library))

    private suspend fun ArtistsViewModel.loaded(): IndexedList<Artist> = artists.filterNotNull().first()

    @Test
    fun `sections match the sort keys, so The Weeknd is under W`() =
        runTest {
            val shown = viewModel().loaded()

            assertEquals(listOf(Section('E', 0), Section('K', 1), Section('W', 2)), shown.sections)
            assertEquals(library, shown.items)
        }

    @Test
    fun `tapping an artist shuffles their own songs`() =
        runTest {
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.onArtistClick(ed)

            val expected =
                PlayRequest(edSongs.map { it.ref }, 0, shuffle = true, PlaySource.ARTIST_SHUFFLE, picked = false)
            assertEquals(listOf(expected), playback.requests)
        }

    @Test
    fun `tapping an artist with no songs plays nothing`() =
        runTest {
            viewModel().onArtistClick(kendrick)

            assertTrue(playback.requests.isEmpty())
        }
}
