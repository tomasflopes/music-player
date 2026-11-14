package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Audit 2026-11-14, S1 (wall clock changes during a song). */
class PlayEventClockAuditTest {
    private var wall = 1_000L
    private var mono = 5_000_000L

    private fun tracker() = PlayEventTracker({ wall }, { mono })

    @Test
    fun `S1 a forward wall clock jump does not inflate listened time`() {
        val tracker = tracker()
        tracker.onStarted(queueEntry(7), 200_000, StartedBy.PICKED, shuffle = false)
        tracker.onPlayingChanged(true)
        wall += 3_600_000
        mono += 5_000

        val skipped = tracker.onStarted(queueEntry(8), 200_000, StartedBy.NAV, shuffle = false)

        assertEquals(5_000L, skipped?.listenedMs)
        assertEquals(1_000L, skipped?.startedAtEpochMs)
    }

    @Test
    fun `S1 a backward wall clock jump does not erase a long listen`() {
        val tracker = tracker()
        tracker.onStarted(queueEntry(7), 200_000, StartedBy.PICKED, shuffle = false)
        tracker.onPlayingChanged(true)
        wall -= 3_600_000
        mono += 150_000

        assertEquals(150_000L, tracker.onStopped()?.listenedMs)
    }
}
