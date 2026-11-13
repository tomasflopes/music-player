package dev.tomasflopes.musicplayer.ui.player

import app.cash.turbine.test
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.playback.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MiniPlayerViewModelTest {
    private val playback = FakePlaybackController()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the mini-player is hidden while nothing is loaded`() =
        runTest {
            MiniPlayerViewModel(playback).visible.test {
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `the mini-player shows once a song is loaded and hides when it is cleared`() =
        runTest {
            MiniPlayerViewModel(playback).visible.test {
                assertEquals(false, awaitItem())

                playback.playbackState.value = PlaybackState(current = testSong(1))
                assertEquals(true, awaitItem())

                playback.playbackState.value = PlaybackState()
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `the mini-player does not re-emit while the same song plays on`() =
        runTest {
            playback.playbackState.value = PlaybackState(current = testSong(1))

            MiniPlayerViewModel(playback).visible.test {
                assertEquals(true, awaitItem())

                playback.playbackState.value = PlaybackState(current = testSong(1), positionMs = 1_000)
                expectNoEvents()
            }
        }

    @Test
    fun `the transport buttons reach the player`() {
        val viewModel = MiniPlayerViewModel(playback)

        viewModel.togglePlayPause()
        viewModel.next()
        viewModel.previous()

        assertEquals(1, playback.togglePlayPauseCalls)
        assertEquals(1, playback.nextCalls)
        assertEquals(1, playback.previousCalls)
    }
}
