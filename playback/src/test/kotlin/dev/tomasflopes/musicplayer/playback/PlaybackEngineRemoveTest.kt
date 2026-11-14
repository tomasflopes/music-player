package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.playback.queue.names
import dev.tomasflopes.musicplayer.playback.queue.refs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackEngineRemoveTest {
    private fun EngineHarness.id(name: Char) = refs(name.toString()).single().songId

    private fun EngineHarness.touched(prefix: String) = state.calls.any { it.startsWith(prefix) }

    @Test
    fun `removing an upcoming song never reloads the player or touches playback`() {
        val harness = EngineHarness("ABCDE")
        harness.startPlaying()
        harness.state.calls.clear()

        harness.engine.removeFromQueue(2, harness.id('C'))

        assertEquals("ABDE", harness.queue.names())
        assertFalse(harness.touched("setMediaItems"))
        assertFalse(harness.touched("pause"))
        assertFalse(harness.touched("seekTo"))
    }

    @Test
    fun `removing a played song keeps the current one playing without a reload`() {
        val harness = EngineHarness("ABCDE")
        harness.queue.jumpTo(3)
        harness.state.itemIndex = 3
        harness.startPlaying()
        harness.state.calls.clear()

        harness.engine.removeFromQueue(1, harness.id('B'))

        assertEquals("ACDE", harness.queue.names())
        assertEquals(
            'D'.code.toLong(),
            harness.queue.current
                ?.ref
                ?.songId
                ?.value,
        )
        assertFalse(harness.touched("setMediaItems"))
        assertFalse(harness.touched("pause"))
    }

    @Test
    fun `a stale place removes nothing`() {
        val harness = EngineHarness("ABC")

        harness.engine.removeFromQueue(1, harness.id('Z'))

        assertEquals("ABC", harness.queue.names())
    }

    @Test
    fun `removing the playing song starts the next one`() {
        val harness = EngineHarness("ABC")
        harness.startPlaying()
        harness.state.calls.clear()

        harness.engine.removeFromQueue(0, harness.id('A'))

        assertEquals("BC", harness.queue.names())
        assertEquals(
            'B'.code.toLong(),
            harness.queue.current
                ?.ref
                ?.songId
                ?.value,
        )
        assertTrue(harness.touched("setMediaItems"))
        assertTrue(harness.touched("play["))
    }

    @Test
    fun `removing the last playing song stops on the one before it`() {
        val harness = EngineHarness("AB")
        harness.queue.jumpTo(1)
        harness.startPlaying()
        harness.state.calls.clear()

        harness.engine.removeFromQueue(1, harness.id('B'))

        assertEquals("A", harness.queue.names())
        assertEquals(0, harness.queue.index)
        assertTrue(harness.touched("pause"))
        assertFalse(harness.touched("play["))
    }

    @Test
    fun `removing the only song empties the queue and stops`() {
        val harness = EngineHarness("A")
        harness.startPlaying()

        harness.engine.removeFromQueue(0, harness.id('A'))

        assertEquals("", harness.queue.names())
        assertEquals(-1, harness.queue.index)
        assertTrue(harness.touched("pause"))
    }

    @Test
    fun `removing the current song while paused loads the next one paused`() {
        val harness = EngineHarness("ABC")
        harness.startPlaying()
        harness.state.playing = false
        harness.state.calls.clear()

        harness.engine.removeFromQueue(0, harness.id('A'))

        assertEquals(
            'B'.code.toLong(),
            harness.queue.current
                ?.ref
                ?.songId
                ?.value,
        )
        assertFalse(harness.touched("play["))
    }

    @Test
    fun `undoing the removal of the playing song makes it current again and plays`() {
        val harness = EngineHarness("ABC")
        harness.startPlaying()
        val removed = removedOf(harness, 0, 'A', wasCurrent = true, playing = true)
        harness.engine.removeFromQueue(0, harness.id('A'))
        harness.state.calls.clear()

        harness.engine.restoreToQueue(removed)

        assertEquals("ABC", harness.queue.names())
        assertEquals(0, harness.queue.index)
        assertTrue(harness.touched("play["))
    }

    @Test
    fun `undoing the removal of an upcoming song puts it back without a reload`() {
        val harness = EngineHarness("ABCDE")
        harness.startPlaying()
        val removed = removedOf(harness, 2, 'C', wasCurrent = false, playing = true)
        harness.engine.removeFromQueue(2, harness.id('C'))
        harness.state.calls.clear()

        harness.engine.restoreToQueue(removed)

        assertEquals("ABCDE", harness.queue.names())
        assertEquals(0, harness.queue.index)
        assertFalse(harness.touched("setMediaItems"))
    }

    @Test
    fun `jumping to a played song keeps the queue and plays it`() {
        val harness = EngineHarness("ABCDE")
        harness.queue.jumpTo(3)
        harness.startPlaying()
        harness.state.calls.clear()

        harness.engine.jumpToQueueIndex(1, harness.id('B'))

        assertEquals("ABCDE", harness.queue.names())
        assertEquals(1, harness.queue.index)
        assertTrue(harness.touched("play["))
    }

    private fun removedOf(
        harness: EngineHarness,
        index: Int,
        name: Char,
        wasCurrent: Boolean,
        playing: Boolean,
    ): RemovedQueueEntry {
        val entry = checkNotNull(harness.queue.entryAt(index))
        return RemovedQueueEntry(
            index = index,
            ref = entry.ref,
            source = entry.source,
            manual = entry.manual,
            playlistId = entry.playlistId,
            wasCurrent = wasCurrent,
            positionMs = 0,
            wasPlaying = playing,
        ).also { check(it.ref.songId == harness.id(name)) }
    }
}
