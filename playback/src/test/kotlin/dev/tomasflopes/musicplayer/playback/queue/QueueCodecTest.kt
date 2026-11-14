package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.random.Random

class QueueCodecTest {
    private fun ref(
        id: Long,
        artist: Long = id % 3,
    ) = SongRef(SongId(id), ArtistId(artist))

    private fun saved(): SavedQueue {
        val list = (1L..6L).map(::ref)
        val entries =
            listOf(
                QueueEntry(list[3], PlaySource.SHUFFLE_ALL, manual = false),
                QueueEntry(list[0], PlaySource.SHUFFLE_ALL, manual = false),
                QueueEntry(list[5], PlaySource.QUEUE, manual = true),
                QueueEntry(list[1], PlaySource.SHUFFLE_ALL, manual = false),
                QueueEntry(ref(99, artist = 7), PlaySource.QUEUE, manual = true),
            )
        return SavedQueue(QueueSnapshot(entries, list, 2, shuffled = true, repeatMode = RepeatMode.ALL), 42_123L)
    }

    @Test
    fun `a saved queue survives the round trip untouched`() {
        val original = saved()

        assertEquals(original, QueueCodec.decode(QueueCodec.encode(original)))
    }

    @Test
    fun `every repeat mode and the unshuffled state round trip`() {
        RepeatMode.entries.forEach { mode ->
            val snapshot = saved().snapshot.copy(shuffled = false, repeatMode = mode)
            val original = SavedQueue(snapshot, 0L)

            assertEquals(original, QueueCodec.decode(QueueCodec.encode(original)))
        }
    }

    @Test
    fun `an empty queue round trips`() {
        val empty = SavedQueue(QueueSnapshot(emptyList(), emptyList(), -1, false, RepeatMode.OFF), 0L)

        assertEquals(empty, QueueCodec.decode(QueueCodec.encode(empty)))
    }

    @Test
    fun `nothing that is not a saved queue decodes`() {
        assertNull(QueueCodec.decode(ByteArray(0)))
        assertNull(QueueCodec.decode(ByteArray(3)))
        assertNull(QueueCodec.decode(Random(1).nextBytes(500)))
        assertNull(QueueCodec.decode("not a queue at all, just some text".toByteArray()))
    }

    @Test
    fun `a file cut short never decodes`() {
        val bytes = QueueCodec.encode(saved())

        (0 until bytes.size).forEach { length ->
            assertNull(QueueCodec.decode(bytes.copyOf(length)), "cut at $length")
        }
    }

    @Test
    fun `one flipped byte anywhere is caught`() {
        val bytes = QueueCodec.encode(saved())

        bytes.indices.forEach { at ->
            val damaged = bytes.copyOf().also { it[at] = (it[at].toInt() xor 0x5A).toByte() }
            assertNull(QueueCodec.decode(damaged), "flipped byte $at")
        }
    }

    @Test
    fun `a state written by another version is ignored, not guessed at`() {
        val bytes = QueueCodec.encode(saved())

        listOf(0, QueueCodec.VERSION + 1, 99).forEach { version ->
            assertNull(QueueCodec.decode(withVersion(bytes, version)), "version $version")
        }
        assertNotNull(QueueCodec.decode(withVersion(bytes, QueueCodec.VERSION)))
    }

    /** Rewrites the version and the checksum, so only the version can be the reason to refuse. */
    private fun withVersion(
        bytes: ByteArray,
        version: Int,
    ): ByteArray {
        val copy = bytes.copyOf()
        ByteBuffer.wrap(copy).putInt(VERSION_OFFSET, version)
        val body = copy.size - CHECKSUM_BYTES
        val crc = CRC32().apply { update(copy, 0, body) }.value.toInt()
        ByteBuffer.wrap(copy).putInt(body, crc)
        return copy
    }

    private companion object {
        const val VERSION_OFFSET = 4
        const val CHECKSUM_BYTES = 4
    }
}
