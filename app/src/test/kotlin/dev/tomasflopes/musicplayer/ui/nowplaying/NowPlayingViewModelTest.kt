package dev.tomasflopes.musicplayer.ui.nowplaying

import dev.tomasflopes.musicplayer.core.model.undo.SingleUndoSlot
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.playback.PlaybackState
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NowPlayingViewModelTest {
    private val playback = FakePlaybackController()
    private val undo = SingleUndoSlot()
    private val viewModel = NowPlayingViewModel(playback, undo)

    @Test
    fun `the state is what the player reports`() {
        val reported =
            PlaybackState(
                current = testSong(1),
                isPlaying = true,
                positionMs = 40_000,
                durationMs = 180_000,
                shuffle = true,
                repeatMode = RepeatMode.ALL,
            )

        playback.playbackState.value = reported

        assertEquals(reported, viewModel.state.value)
    }

    @Test
    fun `seeking asks the player for that position`() {
        viewModel.seekTo(90_000)

        assertEquals(listOf(90_000L), playback.seeks)
    }

    @Test
    fun `the shuffle button turns shuffle on when it is off`() {
        playback.playbackState.value = PlaybackState(current = testSong(1), shuffle = false)

        viewModel.toggleShuffle()

        assertEquals(listOf(true), playback.shuffleCalls)
    }

    @Test
    fun `the shuffle button turns shuffle off when it is on`() {
        playback.playbackState.value = PlaybackState(current = testSong(1), shuffle = true)

        viewModel.toggleShuffle()

        assertEquals(listOf(false), playback.shuffleCalls)
    }

    @Test
    fun `the repeat button asks the player to cycle once per tap`() {
        viewModel.cycleRepeatMode()
        viewModel.cycleRepeatMode()

        assertEquals(2, playback.repeatCycles)
    }

    @Test
    fun `tapping the third upcoming song jumps to index 2`() {
        playback.playbackState.value = PlaybackState(current = testSong(1), upcoming = (2L..5L).map(::testSong))

        viewModel.jumpToUpcoming(2)

        assertEquals(listOf(2), playback.jumps)
    }

    @Test
    fun `the transport buttons reach the player`() {
        viewModel.togglePlayPause()
        viewModel.next()
        viewModel.previous()

        assertEquals(1, playback.togglePlayPauseCalls)
        assertEquals(1, playback.nextCalls)
        assertEquals(1, playback.previousCalls)
    }

    @Test
    fun `the queued songs are marked and the rest of the list is not`() {
        playback.playbackState.value =
            PlaybackState(current = testSong(1), upcoming = (2L..4L).map(::testSong), manualUpcoming = 1)

        val state = viewModel.state.value

        assertTrue(state.isManualUpcoming(0))
        assertFalse(state.isManualUpcoming(1))
    }

    @Test
    fun `the up next list follows the player when shuffle is toggled`() {
        val inOrder = (2L..4L).map(::testSong)
        playback.playbackState.value = PlaybackState(current = testSong(1), upcoming = inOrder, shuffle = false)

        playback.playbackState.value =
            PlaybackState(current = testSong(1), upcoming = inOrder.reversed(), shuffle = true)

        assertEquals(inOrder.reversed(), viewModel.state.value.upcoming)
        assertTrue(viewModel.state.value.shuffle)
    }

    @Test
    fun `jumping sends the place and the song, and does not change the queue`() {
        viewModel.jumpTo(4, testSong(7))

        assertEquals(listOf(4 to testSong(7).id), playback.queueJumps)
        assertTrue(playback.removals.isEmpty())
    }

    @Test
    fun `removing a song asks for that place and offers an undo`() {
        viewModel.remove(3, testSong(7))

        assertEquals(listOf(3 to testSong(7).id), playback.removals)
        assertEquals("Removed \"${testSong(7).title}\" from the queue", undo.pending.value?.message)
    }

    @Test
    fun `undo puts the removed entry back where it was`() =
        kotlinx.coroutines.runBlocking {
            viewModel.remove(3, testSong(7))

            undo.undo(undo.pending.value!!.id)

            assertEquals(listOf(3), playback.restored.map { it.index })
            assertEquals(
                testSong(7).id,
                playback.restored
                    .single()
                    .ref
                    .songId,
            )
        }

    @Test
    fun `a new removal replaces the pending undo, so only the last one comes back`() =
        kotlinx.coroutines.runBlocking {
            viewModel.remove(3, testSong(7))
            val first = undo.pending.value!!
            viewModel.remove(5, testSong(8))

            assertEquals(false, undo.undo(first.id))
            undo.undo(undo.pending.value!!.id)

            assertEquals(listOf(5), playback.restored.map { it.index })
        }
}
