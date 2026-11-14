package dev.tomasflopes.musicplayer.playback.queue

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueSaverTest {
    private class RecordingStore : QueueStore {
        val saves = mutableListOf<SavedQueue?>()

        override fun load(): SavedQueue? = saves.lastOrNull()

        override fun save(saved: SavedQueue?) {
            saves += saved
        }
    }

    private val store = RecordingStore()
    private var position = 0L

    private fun TestScope.saver(queue: PlayQueue) =
        QueueSaver(queue, store, { position }, this, StandardTestDispatcher(testScheduler), DEBOUNCE)

    @Test
    fun `a burst of changes writes once, after the pause, with the position at that moment`() =
        runTest {
            val queue = queue("ABC")
            val saver = saver(queue)

            saver.changed()
            advanceTimeBy(400)
            queue.next()
            saver.changed()
            advanceTimeBy(400)
            saver.changed()
            advanceTimeBy(DEBOUNCE - 100)
            assertEquals(0, store.saves.size)
            position = 7_500L
            advanceUntilIdle()

            val saved = store.saves.single()!!
            assertEquals(1, saved.snapshot.index)
            assertEquals(7_500L, saved.positionMs)
        }

    @Test
    fun `saving now does not wait, and cancels the write that was waiting`() =
        runTest {
            val saver = saver(queue("ABC"))
            position = 1_000L
            saver.changed()

            saver.saveNow()
            advanceUntilIdle()

            assertEquals(1, store.saves.size)
            assertEquals(1_000L, store.saves.single()!!.positionMs)
        }

    @Test
    fun `an empty queue clears the saved state instead of saving an empty one`() =
        runTest {
            val saver = saver(PlayQueue(ReverseShuffle, kotlin.random.Random(1)))

            saver.saveNow()
            advanceUntilIdle()

            assertNull(store.saves.single())
        }

    @Test
    fun `a store that fails does not take the saver down`() =
        runTest {
            val failing =
                object : QueueStore {
                    override fun load(): SavedQueue? = null

                    override fun save(saved: SavedQueue?) = error("disk full")
                }
            val saver = QueueSaver(queue("AB"), failing, { 0L }, this, StandardTestDispatcher(testScheduler), DEBOUNCE)

            saver.saveNow()
            saver.saveBlocking()
            advanceUntilIdle()
        }

    private companion object {
        const val DEBOUNCE = 1_000L
    }
}
