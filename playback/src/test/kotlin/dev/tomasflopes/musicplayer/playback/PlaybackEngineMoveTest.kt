package dev.tomasflopes.musicplayer.playback

import androidx.media3.common.Player
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.playback.queue.names
import dev.tomasflopes.musicplayer.playback.queue.refs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackEngineMoveTest {
    /** A queue of [names] started through a play request, so the player holds the window as it does in the app. */
    private fun playing(
        names: String,
        startIndex: Int = 0,
    ): EngineHarness =
        EngineHarness(names).also {
            it.engine.play(PlayRequest(refs(names), startIndex, false, PlaySource.ALBUM, picked = true))
            it.state.calls.clear()
        }

    private fun EngineHarness.id(name: Char) = refs(name.toString()).single().songId

    private fun EngineHarness.touched(prefix: String) = state.calls.any { it.startsWith(prefix) }

    private fun EngineHarness.countAnnouncements(): () -> Int {
        var announced = 0
        engine.exposed.addListener(
            object : Player.Listener {
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    announced++
                }
            },
        )
        return { announced }
    }

    @Test
    fun `moving an upcoming song reorders the queue without touching playback`() {
        val harness = playing("ABCDE")

        harness.engine.moveInQueue(3, 1, harness.id('D'))

        assertEquals("ADBCE", harness.queue.names())
        assertEquals(0, harness.queue.index)
        assertFalse(harness.touched("pause"))
        assertFalse(harness.touched("seekTo"))
        assertFalse(harness.touched("prepare"))
        assertFalse(harness.touched("setMediaItems"))
    }

    @Test
    fun `the player's window after the current song follows the new order`() {
        val harness = playing("ABCDE")

        harness.engine.moveInQueue(3, 1, harness.id('D'))

        assertTrue(harness.touched("removeMediaItems"))
        assertTrue(harness.touched("addMediaItems"))
        assertEquals(5, harness.state.itemCount)
    }

    @Test
    fun `a stale place moves nothing`() {
        val harness = playing("ABCDE")

        harness.engine.moveInQueue(3, 1, harness.id('Z'))

        assertEquals("ABCDE", harness.queue.names())
        assertTrue(harness.state.calls.isEmpty())
    }

    @Test
    fun `the current and the played songs do not move`() {
        val harness = playing("ABCDE", startIndex = 2)

        harness.engine.moveInQueue(2, 4, harness.id('C'))
        harness.engine.moveInQueue(0, 3, harness.id('A'))

        assertEquals("ABCDE", harness.queue.names())
        assertTrue(harness.state.calls.isEmpty())
    }

    @Test
    fun `a move announces itself so the state republishes`() {
        val harness = playing("ABCDE")
        val announced = harness.countAnnouncements()

        harness.engine.moveInQueue(3, 1, harness.id('D'))

        assertEquals(1, announced())
    }

    @Test
    fun `a move that changes nothing does not announce`() {
        val harness = playing("ABCDE")
        val announced = harness.countAnnouncements()

        harness.engine.moveInQueue(2, 2, harness.id('C'))

        assertEquals(0, announced())
    }
}
