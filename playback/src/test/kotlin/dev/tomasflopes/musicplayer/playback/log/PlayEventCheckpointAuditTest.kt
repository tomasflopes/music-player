package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

/** Audit 2026-11-14, S4 (checkpoint after a kill during a long play). */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayEventCheckpointAuditTest {
    private var wall = 1_000L
    private var mono = 5_000_000L
    private val history = RecordingHistory()
    private val scope = CoroutineScope(UnconfinedTestDispatcher())
    private val checkpoint = Files.createTempDirectory("pending-play").resolve("event").toFile()

    private fun tracker() = PlayEventTracker({ wall }, { mono })

    private fun recorder() = PlayEventRecorder(history, scope, PendingPlayStore(checkpoint) { wall }, tracker())

    private fun tick(ms: Long) {
        wall += ms
        mono += ms
    }

    @Test
    fun `S4 a kill after a long uninterrupted play recovers the time listened`() {
        recorder().started(queueEntry(7), 300_000, StartedBy.PICKED, shuffle = false, playing = true)
        tick(180_000)

        recorder()

        val row = history.events.single()
        assertEquals(EndedBy.STOPPED, row.endedBy)
        assertEquals(180_000L, row.listenedMs)
    }

    @Test
    fun `S4 recovered listened time never exceeds the duration`() {
        recorder().started(queueEntry(7), 100_000, StartedBy.PICKED, shuffle = false, playing = true)
        tick(900_000)

        recorder()

        assertEquals(100_000L, history.events.single().listenedMs)
    }

    @Test
    fun `S4 a paused song recovers only what was listened before the pause`() {
        val first = recorder()
        first.started(queueEntry(7), 300_000, StartedBy.PICKED, shuffle = false, playing = true)
        tick(40_000)
        first.playingChanged(false)
        tick(900_000)

        recorder()

        assertEquals(40_000L, history.events.single().listenedMs)
    }

    @Test
    fun `S4 an old seven field checkpoint is still read`() {
        checkpoint.writeText(
            listOf(7, 1_000, 2_000, 200_000, PlaySource.SONGS.wire, StartedBy.PICKED.wire, false).joinToString("\t"),
        )

        val row = PendingPlayStore(checkpoint) { wall }.takeLeftover()

        assertEquals(SongId(7), row?.songId)
        assertEquals(2_000L, row?.listenedMs)
    }
}
