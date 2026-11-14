package dev.tomasflopes.musicplayer.playback.log

import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.playback.queue.PlayQueue
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import dev.tomasflopes.musicplayer.playback.queue.ReverseShuffle
import dev.tomasflopes.musicplayer.playback.queue.queue
import dev.tomasflopes.musicplayer.playback.queue.refs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlaybackLogTest {
    private var time = 0L
    private var playing = false
    private val history = RecordingHistory()
    private val recorder =
        PlayEventRecorder(history, CoroutineScope(UnconfinedTestDispatcher()), tracker = PlayEventTracker { time })

    private data class Row(
        val song: Char,
        val endedBy: EndedBy,
        val startedBy: StartedBy,
        val listenedMs: Long,
    )

    private fun rows() = history.events.map(::row)

    private fun row(event: PlayEvent) =
        Row(Char(event.songId.value.toInt()), event.endedBy, event.startedBy, event.listenedMs)

    private fun logOver(queue: PlayQueue) = PlaybackLog(recorder, queue, { LIBRARY_DURATION_MS }, { playing })

    private fun startPlayback(log: PlaybackLog) {
        playing = true
        log.playingChanged(true)
    }

    @Test
    fun `next press logs the left song as skipped with its listened time and the new song as nav`() {
        val queue = queue("abc")
        val log = logOver(queue)
        log.started(StartedBy.PICKED)
        startPlayback(log)
        time += 20_000
        queue.next()
        log.started(StartedBy.NAV)
        time += 4_000
        queue.next()
        log.started(StartedBy.NAV)

        assertEquals(
            listOf(
                Row('a', EndedBy.SKIPPED, StartedBy.PICKED, 20_000),
                Row('b', EndedBy.SKIPPED, StartedBy.NAV, 4_000),
            ),
            rows(),
        )
    }

    @Test
    fun `auto advance logs completed with the library duration and starts the next as auto`() {
        val queue = queue("ab")
        val log = logOver(queue)
        log.started(StartedBy.PICKED)
        startPlayback(log)
        time += 90_000
        log.completed()
        queue.advanceOnTrackEnd()
        log.started(StartedBy.AUTO)
        log.completed()

        assertEquals(
            listOf(
                Row('a', EndedBy.COMPLETED, StartedBy.PICKED, LIBRARY_DURATION_MS),
                Row('b', EndedBy.COMPLETED, StartedBy.AUTO, LIBRARY_DURATION_MS),
            ),
            rows(),
        )
        assertEquals(listOf(LIBRARY_DURATION_MS, LIBRARY_DURATION_MS), history.events.map { it.songDurationMs })
    }

    @Test
    fun `queue end logs the last song as completed once`() {
        val log = logOver(queue("a"))
        log.started(StartedBy.PICKED)
        log.completed()
        log.completed()

        assertEquals(listOf(Row('a', EndedBy.COMPLETED, StartedBy.PICKED, LIBRARY_DURATION_MS)), rows())
    }

    @Test
    fun `repeat one logs a completed row and starts the same song again`() {
        val queue = queue("a").apply { repeatMode = RepeatMode.ONE }
        val log = logOver(queue)
        log.started(StartedBy.PICKED)
        log.completed()
        log.started(StartedBy.AUTO)
        log.completed()

        assertEquals(
            listOf(
                Row('a', EndedBy.COMPLETED, StartedBy.PICKED, LIBRARY_DURATION_MS),
                Row('a', EndedBy.COMPLETED, StartedBy.AUTO, LIBRARY_DURATION_MS),
            ),
            rows(),
        )
    }

    @Test
    fun `source is the entry label, queue for manual entries, and shuffle is the queue's mode`() {
        val queue = queue("ab", source = PlaySource.ARTIST, shuffled = true)
        queue.playNext(refs("z"))
        val log = logOver(queue)
        log.started(StartedBy.PICKED)
        queue.next()
        log.started(StartedBy.NAV)
        queue.next()
        log.started(StartedBy.NAV)
        log.completed()

        assertEquals(
            listOf(PlaySource.ARTIST_SHUFFLE, PlaySource.QUEUE, PlaySource.ARTIST_SHUFFLE),
            history.events.map { it.source },
        )
        assertTrue(history.events.all { it.shuffle })
    }

    @Test
    fun `a song that cannot be opened is logged as stopped and the next one starts`() {
        val queue = queue("ab")
        val log = logOver(queue)
        log.started(StartedBy.PICKED)
        log.stopped()
        queue.next()
        log.started(StartedBy.AUTO)
        log.completed()

        assertEquals(
            listOf(
                Row('a', EndedBy.STOPPED, StartedBy.PICKED, 0),
                Row('b', EndedBy.COMPLETED, StartedBy.AUTO, LIBRARY_DURATION_MS),
            ),
            rows(),
        )
    }

    @Test
    fun `nothing is logged when the queue has no current song`() {
        logOver(PlayQueue(ReverseShuffle, Random(1))).started(StartedBy.AUTO)

        assertEquals(emptyList<Row>(), rows())
    }

    private companion object {
        const val LIBRARY_DURATION_MS = 180_000L
    }
}
