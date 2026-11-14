package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.playback.queue.ref
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Audit 2026-11-14, S9 (previous that only restarts the song). */
class PlaybackEnginePreviousAuditTest {
    private val harness = EngineHarness("ABCDE")

    @Test
    fun `S9 previous that restarts the song ends the row as stopped and starts a nav row`() {
        harness.startPlaying()
        harness.time += 10_000
        harness.state.position = 10_000

        harness.engine.previous()

        val first = harness.history.events.single()
        assertEquals(EndedBy.STOPPED, first.endedBy)
        assertEquals(10_000L, first.listenedMs)
        harness.engine.release()
        val second = harness.history.events.last()
        assertEquals(ref('A').songId, second.songId)
        assertEquals(StartedBy.NAV, second.startedBy)
        assertEquals(0L, second.listenedMs)
    }
}
