package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileQueueStoreTest {
    @TempDir lateinit var dir: File

    private fun saved(
        names: String,
        position: Long = 0L,
    ) = SavedQueue(
        QueueSnapshot(
            names.map { QueueEntry(ref(it), PlaySource.ALBUM, manual = false) },
            refs(names),
            0,
            shuffled = false,
            repeatMode = RepeatMode.OFF,
        ),
        position,
    )

    private fun store() = FileQueueStore(File(dir, "state/queue.bin"))

    @Test
    fun `nothing saved means nothing loaded`() {
        assertNull(store().load())
    }

    @Test
    fun `what was saved is loaded, and a newer save replaces it`() {
        val store = store()

        store.save(saved("ABC", position = 5L))
        assertEquals(saved("ABC", position = 5L), store.load())

        store.save(saved("XY", position = 9L))
        assertEquals(saved("XY", position = 9L), store.load())
        assertEquals(listOf("queue.bin"), File(dir, "state").list()!!.toList())
    }

    @Test
    fun `saving nothing clears it`() {
        val store = store()
        store.save(saved("ABC"))

        store.save(null)

        assertNull(store.load())
        assertFalse(File(dir, "state/queue.bin").exists())
    }

    @Test
    fun `a damaged file is ignored, not thrown`() {
        val file = File(dir, "state/queue.bin").also { it.parentFile.mkdirs() }
        file.writeBytes(QueueCodec.encode(saved("ABC")).also { it[10] = (it[10] + 1).toByte() })

        assertNull(FileQueueStore(file).load())

        file.writeText("garbage")
        assertNull(FileQueueStore(file).load())
    }

    @Test
    fun `a file that is really a directory is ignored too`() {
        val file = File(dir, "queue.bin").also { it.mkdirs() }

        assertNull(FileQueueStore(file).load())
    }
}
