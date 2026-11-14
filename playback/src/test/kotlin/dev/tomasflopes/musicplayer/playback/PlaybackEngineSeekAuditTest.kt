package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.playback.queue.ref
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Audit 2026-11-14, S2 (controller seek to a window index). */
class PlaybackEngineSeekAuditTest {
    private val harness = EngineHarness("ABCDE")

    @Test
    fun `S2 a controller seek to a window index moves the queue and logs a picked row`() {
        harness.startPlaying()
        harness.time += 20_000

        harness.engine.exposed.seekTo(2, 0L)

        assertEquals(2, harness.queue.index)
        val skipped = harness.history.events.single()
        assertEquals(ref('A').songId, skipped.songId)
        assertEquals(EndedBy.SKIPPED, skipped.endedBy)
        harness.engine.release()
        val picked = harness.history.events.last()
        assertEquals(ref('C').songId, picked.songId)
        assertEquals(StartedBy.PICKED, picked.startedBy)
    }
}
