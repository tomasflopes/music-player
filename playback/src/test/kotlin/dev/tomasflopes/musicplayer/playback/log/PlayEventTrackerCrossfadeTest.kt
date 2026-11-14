package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The listening clock across a crossfade (ADR 0062): the overlap is booked once, to the outgoing row. */
class PlayEventTrackerCrossfadeTest {
    private var time = 1_000L
    private val tracker = PlayEventTracker({ time }, { time })

    private fun startOutgoing() {
        tracker.onStarted(queueEntry(1), 200_000, StartedBy.PICKED, shuffle = false)
        tracker.onPlayingChanged(true)
    }

    @Test
    fun `a held row listens to nothing until the hold is released`() {
        startOutgoing()
        time += 6_000
        tracker.onEndedNaturally()
        tracker.onStarted(queueEntry(2), 200_000, StartedBy.AUTO, shuffle = false, playing = true, holdClock = true)
        time += 4_000

        tracker.releaseHold(playing = true)
        time += 10_000

        assertEquals(10_000L, tracker.onStopped()?.listenedMs)
    }

    @Test
    fun `the player reporting play during the hold does not start the clock`() {
        tracker.onStarted(queueEntry(2), 200_000, StartedBy.AUTO, shuffle = false, playing = false, holdClock = true)

        tracker.onPlayingChanged(false)
        time += 1_000
        tracker.onPlayingChanged(true)
        time += 3_000
        tracker.releaseHold(playing = true)
        time += 2_000

        assertEquals(2_000L, tracker.onStopped()?.listenedMs)
    }

    @Test
    fun `releasing while paused waits for the next play`() {
        tracker.onStarted(queueEntry(2), 200_000, StartedBy.AUTO, shuffle = false, playing = false, holdClock = true)
        tracker.releaseHold(playing = false)
        time += 9_000

        tracker.onPlayingChanged(true)
        time += 1_500

        assertEquals(1_500L, tracker.onStopped()?.listenedMs)
    }

    @Test
    fun `the incoming song skipped during the fade is a skip with no listening`() {
        tracker.onStarted(queueEntry(2), 200_000, StartedBy.AUTO, shuffle = false, playing = true, holdClock = true)
        time += 2_000

        val skipped = tracker.onStarted(queueEntry(3), 200_000, StartedBy.NAV, shuffle = false, playing = true)

        assertEquals(0L, skipped?.listenedMs)
        assertEquals(EndedBy.SKIPPED, skipped?.endedBy)
    }

    @Test
    fun `the row after a held one is not held`() {
        tracker.onStarted(queueEntry(2), 200_000, StartedBy.AUTO, shuffle = false, playing = true, holdClock = true)
        tracker.onStarted(queueEntry(3), 200_000, StartedBy.NAV, shuffle = false, playing = true)
        time += 5_000

        assertEquals(5_000L, tracker.onStopped()?.listenedMs)
    }

    @Test
    fun `release with nothing playing changes nothing`() {
        tracker.releaseHold(playing = true)

        assertNull(tracker.onStopped())
    }

    @Test
    fun `listened time on the monotonic clock ignores a wall clock jump during the fade`() {
        var wall = 1_000L
        var mono = 1_000L
        val split = PlayEventTracker({ wall }, { mono })
        split.onStarted(queueEntry(2), 200_000, StartedBy.AUTO, shuffle = false, playing = true, holdClock = true)
        mono += 4_000
        wall -= 3_600_000
        split.releaseHold(playing = true)
        mono += 7_000
        wall += 3_600_000

        assertEquals(7_000L, split.onStopped()?.listenedMs)
    }

    @Test
    fun `the checkpoint of a held row knows no listening yet`() {
        tracker.onStarted(queueEntry(2), 200_000, StartedBy.AUTO, shuffle = false, playing = true, holdClock = true)
        time += 3_000

        assertEquals(0L, tracker.pending()?.event?.listenedMs)
        assertNull(tracker.pending()?.playingSinceWallMs)
    }
}
