package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.taste.shuffle.PlainShuffle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlayQueueTest {
    @Test
    fun `play next goes right after current, add to queue goes after those`() {
        val queue = queue("ABC")

        queue.addToQueue(refs("X"))
        queue.playNext(refs("Y"))
        queue.addToQueue(refs("Z"))

        assertEquals("AYXZBC", queue.names())
        assertEquals('A', queue.current?.name)
    }

    @Test
    fun `manual entries always precede non-manual upcoming entries`() {
        val queue = queue("ABC")
        queue.addToQueue(refs("X"))
        queue.next()

        queue.addToQueue(refs("Z"))

        assertEquals("AXZBC", queue.names())
        assertEquals('X', queue.current?.name)
        assertTrue(
            queue
                .window(0, 10)
                .drop(1)
                .takeWhile { it.manual }
                .map { it.name } == listOf('Z'),
        )
    }

    @Test
    fun `queued entries carry source QUEUE and are manual`() {
        val queue = queue("A", source = PlaySource.ARTIST)

        queue.playNext(refs("Y"))

        val y = queue.window(0, 1)[1]
        assertEquals(PlaySource.QUEUE, y.source)
        assertTrue(y.manual)
        assertEquals(false, queue.current?.manual)
    }

    @Test
    fun `replace keeps queued songs after the new current`() {
        val queue = queue("ABC")
        queue.addToQueue(refs("X"))
        queue.playNext(refs("Y"))
        queue.addToQueue(refs("Z"))

        queue.replace(refs("NOP"), 0, PlaySource.ALBUM, shuffled = false)

        assertEquals("NYXZOP", queue.names())
        assertEquals('N', queue.current?.name)
    }

    @Test
    fun `replace drops manual entries that were already played`() {
        val queue = queue("AB")
        queue.playNext(refs("Y"))
        queue.next()
        assertEquals('Y', queue.current?.name)

        queue.replace(refs("N"), 0, PlaySource.ALBUM, shuffled = false)

        assertEquals("N", queue.names())
    }

    @Test
    fun `tapping a song plays the list in order`() {
        val queue = queue("ABCD", startIndex = 1, source = PlaySource.ARTIST)

        assertEquals('B', queue.current?.name)
        assertEquals(1, queue.index)
        assertEquals('C', queue.next()?.name)
        assertEquals('D', queue.next()?.name)
        assertEquals('C', queue.previous()?.name)
        assertEquals('B', queue.previous()?.name)
        assertEquals('A', queue.previous()?.name)
        assertNull(queue.previous())
        assertEquals(0, queue.index)
    }

    @Test
    fun `advance on track end moves to the next song like next`() {
        val queue = queue("AB")

        assertEquals('B', queue.advanceOnTrackEnd()?.name)
    }

    @Test
    fun `window at index 5 of 16000 with 20 and 20 has 26 entries`() {
        val songs = (0 until 16_000).map { SongRef(SongId(it.toLong()), ArtistId(it % 50L)) }
        val queue = PlayQueue(PlainShuffle(), Random(1))
        queue.replace(songs, startIndex = 5, PlaySource.SONGS, shuffled = false)

        val window = queue.window(behind = 20, ahead = 20)

        assertEquals(26, window.size)
        assertEquals(songs[0], window.first().ref)
        assertEquals(songs[5], window[5].ref)
        assertEquals(songs[25], window.last().ref)
    }

    @Test
    fun `shuffle all of 16000 starts well inside the 500 ms phone budget`() {
        val songs = (0 until 16_000).map { SongRef(SongId(it.toLong()), ArtistId(it % 50L)) }
        val queue = PlayQueue(PlainShuffle(), Random(1))
        queue.replace(songs, 0, PlaySource.SHUFFLE_ALL, shuffled = true)

        val start = System.nanoTime()
        queue.replace(songs, 0, PlaySource.SHUFFLE_ALL, shuffled = true)
        queue.window(20, 20)
        val ms = (System.nanoTime() - start) / 1_000_000

        assertTrue(ms < 200, "took $ms ms")
        assertEquals(16_000, queue.size)
    }

    @Test
    fun `removeAt an upcoming index leaves current unchanged`() {
        val queue = queue("ABCD", startIndex = 1)

        queue.removeAt(2)

        assertEquals("ABD", queue.names())
        assertEquals('B', queue.current?.name)
        assertEquals(1, queue.index)
    }

    @Test
    fun `removeAt before current moves the index back with the song`() {
        val queue = queue("ABCD", startIndex = 2)

        queue.removeAt(0)

        assertEquals('C', queue.current?.name)
        assertEquals(1, queue.index)
    }

    @Test
    fun `jumpTo makes that entry current`() {
        val queue = queue("ABCD")

        assertEquals('D', queue.jumpTo(3)?.name)
        assertEquals(3, queue.index)
        assertNull(queue.jumpTo(9))
    }

    @Test
    fun `an empty queue has no current`() {
        val queue = PlayQueue(PlainShuffle(), Random(1))

        assertNull(queue.current)
        assertNull(queue.next())
        assertEquals(0, queue.size)
    }

    @Test
    fun `snapshot carries what a restart needs`() {
        val queue = queue("ABC", startIndex = 1, source = PlaySource.ARTIST, shuffled = true)
        queue.repeatMode = RepeatMode.ALL

        val snapshot = queue.snapshot()

        assertEquals(queue.index, snapshot.index)
        assertEquals(true, snapshot.shuffled)
        assertEquals(RepeatMode.ALL, snapshot.repeatMode)
        assertEquals(queue.window(queue.size, queue.size), snapshot.entries)
        assertEquals(refs("ABC"), snapshot.originalOrder)
    }

    @Test
    fun `restore rebuilds the same queue`() {
        val queue = queue("ABCDE", startIndex = 1, source = PlaySource.ARTIST, shuffled = true)
        queue.playNext(refs("Y"))
        queue.repeatMode = RepeatMode.ONE

        val restored = PlayQueue(ReverseShuffle, Random(1)).apply { restore(queue.snapshot()) }

        assertEquals(queue.names(), restored.names())
        assertEquals(queue.index, restored.index)
        restored.setShuffled(false)
        assertEquals('B', restored.current?.name)
        assertEquals("ABYCDE", restored.names())
    }
}
