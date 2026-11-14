package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry
import dev.tomasflopes.musicplayer.playback.queue.QueueSnapshot
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import dev.tomasflopes.musicplayer.playback.queue.SavedQueue
import dev.tomasflopes.musicplayer.playback.queue.name
import dev.tomasflopes.musicplayer.playback.queue.names
import dev.tomasflopes.musicplayer.playback.queue.ref
import dev.tomasflopes.musicplayer.playback.queue.refs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Queue persistence (ADR 0036): restore on service start, paused, with no log row until the owner plays. */
class PlaybackEngineRestoreTest {
    private fun saved(
        names: String = "CADBE",
        index: Int = 2,
        position: Long = 42_000L,
        manual: String = "",
    ): SavedQueue {
        val entries = names.map { QueueEntry(ref(it), PlaySource.SHUFFLE_ALL, manual = it in manual) }
        val snapshot = QueueSnapshot(entries, refs("ABCDE"), index, shuffled = true, repeatMode = RepeatMode.ALL)
        return SavedQueue(snapshot, position)
    }

    private fun harness(
        saved: SavedQueue?,
        retired: String = "",
    ) = EngineHarness(
        "",
        store = FixedQueueStore(saved),
        inLibrary = { id: SongId -> id.value.toInt().toChar() !in retired },
    )

    private val EngineHarness.calls get() = state.calls.map { it.substringBefore('[') }

    @Test
    fun `restore rebuilds the queue and parks the player paused at the saved position`() {
        val harness = harness(saved(manual = "D"))

        harness.engine.restore()

        assertEquals("CADBE", harness.queue.names())
        assertEquals(2, harness.queue.index)
        assertTrue(harness.queue.shuffled)
        assertEquals(RepeatMode.ALL, harness.queue.repeatMode)
        assertTrue(harness.queue.current!!.manual)
        assertTrue("setMediaItems" in harness.calls)
        assertTrue("prepare" in harness.calls)
        assertTrue("seekTo[42000]" in harness.state.calls)
        assertTrue("pause" in harness.calls)
        assertFalse("play" in harness.calls)
    }

    @Test
    fun `restoring writes no play event, not even when the service closes`() {
        val harness = harness(saved())

        harness.engine.restore()
        harness.time += 60_000
        harness.engine.release()

        assertTrue(harness.history.events.isEmpty())
    }

    @Test
    fun `the first play after a restore starts the one row, as auto`() {
        val harness = harness(saved())
        harness.engine.restore()

        harness.state.playing = true
        harness.state.listener?.onIsPlayingChanged(true)
        harness.time += 5_000
        harness.engine.release()

        val row = harness.history.events.single()
        assertEquals(ref('D').songId, row.songId)
        assertEquals(StartedBy.AUTO, row.startedBy)
        assertEquals(EndedBy.STOPPED, row.endedBy)
        assertEquals(5_000L, row.listenedMs)
    }

    @Test
    fun `pressing next while still paused after a restore does not log a row twice`() {
        val harness = harness(saved())
        harness.engine.restore()

        harness.engine.next()
        harness.state.playing = true
        harness.state.listener?.onIsPlayingChanged(true)
        harness.time += 1_000
        harness.engine.release()

        val row = harness.history.events.single()
        assertEquals(StartedBy.NAV, row.startedBy)
        assertEquals(1_000L, row.listenedMs)
    }

    @Test
    fun `songs retired since the save are skipped`() {
        val harness = harness(saved(), retired = "AD")

        harness.engine.restore()

        assertEquals("CBE", harness.queue.names())
        assertEquals('B', harness.queue.current?.name)
        assertFalse("seekTo[42000]" in harness.state.calls)
    }

    @Test
    fun `nothing saved, everything retired or a failing store leave the queue empty and the player alone`() {
        listOf(
            harness(null),
            harness(saved(), retired = "ABCDE"),
            EngineHarness("", store = FixedQueueStore(saved(), failing = true)),
        ).forEach { harness ->
            harness.engine.restore()

            assertEquals(0, harness.queue.size)
            assertFalse("setMediaItems" in harness.calls)
            assertFalse("prepare" in harness.calls)
        }
    }

    @Test
    fun `a queue the owner already started is not replaced by the saved one`() {
        val harness = EngineHarness("XYZ", store = FixedQueueStore(saved()))

        harness.engine.restore()

        assertEquals("XYZ", harness.queue.names())
    }

    @Test
    fun `changes ask the saver for a debounced save, pausing and closing ask for an immediate one`() {
        val saving = RecordingSaving()
        val harness = EngineHarness("ABCDE", saving = saving)
        harness.startPlaying()
        val afterStart = saving.changed

        harness.engine.jumpToUpcoming(0)
        harness.engine.playNext(refs("Z"))
        harness.engine.setShuffle(true)
        harness.engine.setRepeat(RepeatMode.ALL)
        assertTrue(saving.changed >= afterStart + 4, "changed=${saving.changed}")

        harness.state.playing = false
        harness.state.listener?.onIsPlayingChanged(false)
        assertEquals(1, saving.now)

        harness.engine.release()
        assertEquals(1, saving.blocking)
    }

    @Test
    fun `while playing the position is saved on an interval, never on every tick`() {
        val saving = RecordingSaving()
        val harness = EngineHarness("ABC", saving = saving, config = PlaybackConfig(positionSaveIntervalMs = 40))
        harness.startPlaying()

        val deadline = System.currentTimeMillis() + 3_000
        while (saving.now < 2 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(saving.now >= 2, "interval saves: ${saving.now}")

        harness.state.playing = false
        harness.state.listener?.onIsPlayingChanged(false)
        val atPause = saving.now
        Thread.sleep(150)
        assertEquals(atPause, saving.now)
        harness.engine.release()
    }
}
