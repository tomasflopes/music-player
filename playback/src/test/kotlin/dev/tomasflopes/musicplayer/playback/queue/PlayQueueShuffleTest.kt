package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlayQueueShuffleTest {
    @Test
    fun `shuffle on keeps the current song and reorders only what is left`() {
        val queue = queue("ABCDE", startIndex = 1)

        queue.setShuffled(true)

        assertEquals("ABEDC", queue.names())
        assertEquals('B', queue.current?.name)
        assertEquals(1, queue.index)
    }

    @Test
    fun `a shuffled play with no picked song starts on the shuffle's first song, not the list's`() {
        val queue = PlayQueue(ReverseShuffle, Random(1))

        queue.replace(refs("ABCDE"), 0, PlaySource.SHUFFLE_ALL, shuffled = true, randomStart = true)

        assertEquals("EDCBA", queue.names())
        assertEquals('E', queue.current?.name)
        assertEquals(0, queue.index)
    }

    @Test
    fun `a shuffled play from a picked song starts on it`() {
        val queue = PlayQueue(ReverseShuffle, Random(1))

        queue.replace(refs("ABCDE"), 2, PlaySource.ARTIST_SHUFFLE, shuffled = true)

        assertEquals("CEDBA", queue.names())
        assertEquals('C', queue.current?.name)
    }

    @Test
    fun `shuffle off restores the original order around the current song`() {
        val queue = queue("ABCDE", startIndex = 1)
        queue.setShuffled(true)
        queue.next()
        assertEquals('E', queue.current?.name)

        queue.setShuffled(false)

        assertEquals("ABCDE", queue.names())
        assertEquals('E', queue.current?.name)
        assertEquals(4, queue.index)
        assertEquals('D', queue.previous()?.name)
    }

    @Test
    fun `manual entries do not move in either direction`() {
        val queue = queue("ABC")
        queue.playNext(refs("Y"))
        assertEquals("AYBC", queue.names())

        queue.setShuffled(true)
        assertEquals("AYCB", queue.names())

        queue.setShuffled(false)
        assertEquals("AYBC", queue.names())
    }

    @Test
    fun `toggle relabels artist and songs sources only`() {
        val artist = queue("AB", source = PlaySource.ARTIST)
        artist.playNext(refs("Y"))
        artist.setShuffled(true)
        assertEquals(listOf(PlaySource.QUEUE, PlaySource.ARTIST_SHUFFLE), artist.window(0, 5).drop(1).map { it.source })
        artist.setShuffled(false)
        assertEquals(PlaySource.ARTIST, artist.window(0, 5).last().source)

        val album = queue("AB", source = PlaySource.ALBUM)
        album.setShuffled(true)
        assertEquals(PlaySource.ALBUM, album.window(0, 5).last().source)

        val songs = queue("AB", source = PlaySource.SONGS)
        songs.setShuffled(true)
        assertEquals(PlaySource.SHUFFLE_ALL, songs.window(0, 5).last().source)
    }

    @Test
    fun `starting shuffled puts the tapped song first`() {
        val queue = queue("ABCDE", startIndex = 2, shuffled = true)

        assertEquals('C', queue.current?.name)
        assertEquals(0, queue.index)
        assertEquals("CEDBA", queue.names())
    }

    @Test
    fun `shuffled reflects the last replace or toggle`() {
        val queue = queue("AB", shuffled = true)
        assertTrue(queue.shuffled)

        queue.setShuffled(false)
        assertFalse(queue.shuffled)

        queue.replace(refs("CD"), 0, PlaySource.ALBUM, shuffled = true)
        assertTrue(queue.shuffled)
    }

    @Test
    fun `shuffle off after starting shuffled puts the current back in list position`() {
        val queue = queue("ABCDE", startIndex = 2, shuffled = true)

        queue.setShuffled(false)

        assertEquals("ABCDE", queue.names())
        assertEquals('C', queue.current?.name)
        assertEquals(2, queue.index)
        assertEquals('D', queue.next()?.name)
    }

    @Test
    fun `turning shuffle on twice is harmless`() {
        val queue = queue("ABC")

        queue.setShuffled(true)
        val once = queue.names()
        queue.setShuffled(true)

        assertEquals(once, queue.names())
    }
}
