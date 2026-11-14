package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PlayEventRecorderTest {
    private var time = 1_000L
    private val history = RecordingHistory()
    private val scope = CoroutineScope(UnconfinedTestDispatcher())
    private val checkpoint = Files.createTempDirectory("pending-play").resolve("event").toFile()

    /** A new recorder is what the service builds when the process starts again. */
    private fun recorder() = PlayEventRecorder(history, scope, PendingPlayStore(checkpoint), PlayEventTracker { time })

    @Test
    fun `starting a song refreshes the pending checkpoint`() {
        val recorder = recorder()
        recorder.started(queueEntry(7), 200_000, StartedBy.PICKED, shuffle = false)
        recorder.started(queueEntry(8), 200_000, StartedBy.NAV, shuffle = false, playing = true)

        assertEquals(SongId(8), PendingPlayStore(checkpoint).takeLeftover()?.songId)
    }

    @Test
    fun `a crash right after a skip appends the skipped song once and the new one as stopped`() {
        recorder().apply {
            started(queueEntry(7), 200_000, StartedBy.PICKED, shuffle = false)
            started(queueEntry(8), 200_000, StartedBy.NAV, shuffle = false, playing = true)
        }

        recorder()

        assertEquals(
            listOf(SongId(7) to EndedBy.SKIPPED, SongId(8) to EndedBy.STOPPED),
            history.events.map { it.songId to it.endedBy },
        )
    }
}
