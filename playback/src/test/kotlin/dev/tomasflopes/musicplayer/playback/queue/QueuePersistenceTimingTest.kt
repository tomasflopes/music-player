package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.taste.shuffle.PlainShuffle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.random.Random

/** The queue can hold the whole library: 16 000 songs. Prints the medians; asserts only a loose bound. */
class QueuePersistenceTimingTest {
    @TempDir lateinit var dir: File

    private fun median(block: () -> Unit): Long {
        repeat(WARMUPS) { block() }
        val times =
            (1..RUNS).map {
                val start = System.nanoTime()
                block()
                (System.nanoTime() - start) / NANOS_PER_MS
            }
        return times.sorted()[RUNS / 2]
    }

    @Test
    fun `saving and restoring 16000 songs is fast and the file is small`() {
        val random = Random(SEED)
        val library = (1..SONGS).map { SongRef(SongId(it * 7L), ArtistId(random.nextLong(ARTISTS))) }
        val queue = PlayQueue(PlainShuffle(), random)
        queue.replace(library, startIndex = SONGS / 2, source = PlaySource.SHUFFLE_ALL, shuffled = true)
        queue.next()
        queue.playNext(library.take(2))
        queue.addToQueue(library.takeLast(1))
        queue.repeatMode = RepeatMode.ALL
        val store = FileQueueStore(File(dir, "queue.bin"))
        val inLibrary = library.map { it.songId }.toSet()
        val expected = queue.snapshot()

        val saveMs = median { store.save(SavedQueue(queue.snapshot(), POSITION)) }
        val restored = PlayQueue(PlainShuffle(), Random(1))
        val restoreMs =
            median {
                val resolved = QueueRestorer.resolve(store.load()!!, inLibrary)!!
                restored.restore(resolved.snapshot)
            }

        println("QUEUE-PERSISTENCE $SONGS songs: save median ${saveMs}ms, restore median ${restoreMs}ms")
        println("QUEUE-PERSISTENCE file size ${size()} bytes")
        assertEquals(expected, restored.snapshot())
        assertTrue(saveMs < LOOSE_BOUND_MS, "save took $saveMs ms")
        assertTrue(restoreMs < LOOSE_BOUND_MS, "restore took $restoreMs ms")
        assertTrue(size() < SONGS * MAX_BYTES_PER_SONG, "file is ${size()} bytes")
    }

    private fun size() = File(dir, "queue.bin").length()

    private companion object {
        const val SONGS = 16_000
        const val ARTISTS = 700L
        const val SEED = 20_261_114
        const val POSITION = 93_000L
        const val WARMUPS = 3
        const val RUNS = 9
        const val NANOS_PER_MS = 1_000_000L
        const val LOOSE_BOUND_MS = 2_000L
        const val MAX_BYTES_PER_SONG = 48
    }
}
