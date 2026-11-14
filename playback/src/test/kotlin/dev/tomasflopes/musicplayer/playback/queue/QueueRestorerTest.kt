package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QueueRestorerTest {
    private fun saved(
        names: String,
        index: Int,
        position: Long = 30_000L,
        manual: String = "",
        shuffled: Boolean = true,
        repeat: RepeatMode = RepeatMode.ALL,
    ): SavedQueue {
        val entries = names.map { QueueEntry(ref(it), PlaySource.SHUFFLE_ALL, manual = it in manual) }
        val original = refs(names.filter { it !in manual })
        return SavedQueue(QueueSnapshot(entries, original, index, shuffled, repeat), position)
    }

    private fun alive(names: String) = names.map { SongId(it.code.toLong()) }.toSet()

    private fun SavedQueue.names() = snapshot.entries.map { it.name }.joinToString("")

    @Test
    fun `when every song is still there nothing changes`() {
        val original = saved("CADBE", index = 2, manual = "D")

        assertEquals(original, QueueRestorer.resolve(original, alive("ABCDE")))
    }

    @Test
    fun `retired songs are skipped and the current song keeps its place and position`() {
        val resolved = QueueRestorer.resolve(saved("CADBE", index = 2), alive("CDBE"))!!

        assertEquals("CDBE", resolved.names())
        assertEquals('D', resolved.snapshot.entries[resolved.snapshot.index].name)
        assertEquals(30_000L, resolved.positionMs)
    }

    @Test
    fun `retired songs leave the original order too, so unshuffling still works`() {
        val resolved = QueueRestorer.resolve(saved("CADBE", index = 0), alive("CDBE"))!!

        assertEquals(
            "CDBE",
            resolved.snapshot.originalOrder
                .map {
                    it.songId.value
                        .toInt()
                        .toChar()
                }.joinToString(""),
        )
    }

    @Test
    fun `when the current song is gone the next surviving one takes over from the start`() {
        val resolved = QueueRestorer.resolve(saved("CADBE", index = 2), alive("CABE"))!!

        assertEquals("CABE", resolved.names())
        assertEquals('B', resolved.snapshot.entries[resolved.snapshot.index].name)
        assertEquals(0L, resolved.positionMs)
    }

    @Test
    fun `when the current song was last and is gone the one before it takes over`() {
        val resolved = QueueRestorer.resolve(saved("CADBE", index = 4), alive("CADB"))!!

        assertEquals('B', resolved.snapshot.entries[resolved.snapshot.index].name)
        assertEquals(0L, resolved.positionMs)
    }

    @Test
    fun `manual entries stay, in place, and the modes carry over`() {
        val resolved = QueueRestorer.resolve(saved("CADBE", index = 0, manual = "AD"), alive("CADE"))!!

        assertEquals("CADE", resolved.names())
        assertEquals(listOf(false, true, true, false), resolved.snapshot.entries.map { it.manual })
        assertEquals(true, resolved.snapshot.shuffled)
        assertEquals(RepeatMode.ALL, resolved.snapshot.repeatMode)
    }

    @Test
    fun `no song left means no queue`() {
        assertNull(QueueRestorer.resolve(saved("CADBE", index = 2), alive("XYZ")))
        assertNull(QueueRestorer.resolve(saved("CADBE", index = 2), emptySet()))
    }
}
