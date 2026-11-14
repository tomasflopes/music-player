package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.taste.shuffle.PlainShuffle
import dev.tomasflopes.musicplayer.core.taste.shuffle.ShuffleStrategy
import dev.tomasflopes.musicplayer.core.taste.shuffle.SmartShuffle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Smart shuffle (design 7.8, ADR 0035) as the queue uses it. */
class SmartShuffleQueueTest {
    private val minDistance = 5

    private fun song(
        id: Int,
        artist: Int,
    ) = SongRef(SongId(id.toLong()), ArtistId(artist.toLong()))

    private fun library(
        artists: Int = 20,
        each: Int = 5,
    ) = (0 until artists * each).map { song(it, it % artists) }

    private fun smartQueue(seed: Int = 1) = PlayQueue(SmartShuffle(), Random(seed), artistShuffle = PlainShuffle())

    private fun PlayQueue.songs() = window(behind = size, ahead = size).map { it.ref }

    private fun closest(order: List<SongRef>): Int {
        val last = HashMap<ArtistId, Int>()
        var best = Int.MAX_VALUE
        order.forEachIndexed { i, s -> last.put(s.mainArtistId, i)?.let { best = minOf(best, i - it) } }
        return best
    }

    @Test
    fun `shuffle all keeps every artist out of the next 4 tracks`() {
        for (seed in 1..30) {
            val queue = smartQueue(seed)

            queue.replace(library(), 0, PlaySource.SHUFFLE_ALL, shuffled = true, randomStart = true)

            assertTrue(closest(queue.songs()) >= minDistance, "seed $seed")
        }
    }

    @Test
    fun `a tapped song starts the shuffle and the gap holds from it`() {
        val songs = library()
        for (seed in 1..30) {
            val queue = smartQueue(seed)

            queue.replace(songs, 7, PlaySource.SONGS, shuffled = true)

            assertEquals(songs[7], queue.current?.ref)
            assertTrue(closest(queue.songs()) >= minDistance, "seed $seed")
        }
    }

    @Test
    fun `turning shuffle on mid-song keeps the song and keeps the next tracks off its artist`() {
        val songs = library()
        for (seed in 1..30) {
            val queue = smartQueue(seed)
            queue.replace(songs, 3, PlaySource.SONGS, shuffled = false)
            queue.jumpTo(10)

            queue.setShuffled(true)

            assertEquals(songs[10], queue.current?.ref)
            assertEquals(10, queue.index)
            val fromCurrent = queue.songs().drop(queue.index)
            assertEquals(songs.drop(10).toSet(), fromCurrent.toSet())
            assertTrue(closest(fromCurrent) >= minDistance, "seed $seed")
        }
    }

    @Test
    fun `manual entries keep their place and count as neighbours`() {
        val songs = library()
        val manual = listOf(song(1_001, 98), song(1_002, 99), song(1_003, 97))
        for (seed in 1..30) {
            val queue = smartQueue(seed)
            queue.replace(songs, 3, PlaySource.SONGS, shuffled = false)
            queue.playNext(manual.take(1))
            queue.addToQueue(manual.drop(1))

            queue.setShuffled(true)

            val fromCurrent = queue.songs().drop(queue.index)
            assertEquals(manual, fromCurrent.drop(1).take(3))
            assertTrue(closest(fromCurrent) >= minDistance, "seed $seed")
        }
    }

    @Test
    fun `artist shuffle is not spaced`() {
        val songs = (0 until 30).map { song(it, 0) } + listOf(song(100, 1), song(101, 1), song(102, 2), song(103, 2))
        val queue = smartQueue(5)

        queue.replace(songs, 0, PlaySource.ARTIST_SHUFFLE, shuffled = true, randomStart = true)

        assertEquals(PlainShuffle().order(songs, Random(5)), queue.songs())
    }

    @Test
    fun `shuffling an artist list on after the fact is not spaced either`() {
        val songs = (0 until 30).map { song(it, 0) } + listOf(song(100, 1), song(101, 2))
        val queue = smartQueue(5)
        queue.replace(songs, 0, PlaySource.ARTIST, shuffled = false)

        queue.setShuffled(true)

        assertEquals(listOf(songs[0]) + PlainShuffle().order(songs.drop(1), Random(5)), queue.songs())
    }

    @Test
    fun `playing in order never shuffles, whatever the strategy`() {
        val failing = ShuffleStrategy { _, _ -> error("must not shuffle") }
        val album = library(artists = 2, each = 6)
        for (source in listOf(PlaySource.ALBUM, PlaySource.FOLDER)) {
            val queue = PlayQueue(failing, Random(1), artistShuffle = failing)

            queue.replace(album, 2, source, shuffled = false)

            assertEquals(album, queue.songs(), source.name)
            assertEquals(2, queue.index)
        }
    }

    @Test
    fun `repeat all reshuffles without breaking the gap across the wrap`() {
        for (seed in 1..30) {
            val queue = smartQueue(seed)
            queue.repeatMode = RepeatMode.ALL
            queue.replace(library(), 0, PlaySource.SHUFFLE_ALL, shuffled = true, randomStart = true)
            val firstRound = queue.songs()
            queue.jumpTo(queue.size - 1)

            queue.next()

            assertEquals(0, queue.index)
            assertTrue(closest(firstRound + queue.songs()) >= minDistance, "seed $seed")
        }
    }
}
