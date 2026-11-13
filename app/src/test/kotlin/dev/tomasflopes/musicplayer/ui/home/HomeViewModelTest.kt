package dev.tomasflopes.musicplayer.ui.home

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val playback = FakePlaybackController()
    private val scanner = FakeScanController()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shuffle all sends every song once, shuffled, as shuffle all`() {
        val refs = listOf(SongRef(SongId(1), ArtistId(1)), SongRef(SongId(2), ArtistId(2)))

        HomeViewModel(playback, FakeSongReader(refs), scanner).shuffleAll()

        assertEquals(
            listOf(PlayRequest(refs, 0, shuffle = true, PlaySource.SHUFFLE_ALL, picked = false)),
            playback.requests,
        )
    }

    @Test
    fun `shuffle all does nothing while the library is empty`() {
        HomeViewModel(playback, FakeSongReader(), scanner).shuffleAll()

        assertTrue(playback.requests.isEmpty())
    }

    @Test
    fun `rescan asks the scan controller once`() {
        HomeViewModel(playback, FakeSongReader(), scanner).rescan()

        assertEquals(1, scanner.scanRequests)
    }
}
