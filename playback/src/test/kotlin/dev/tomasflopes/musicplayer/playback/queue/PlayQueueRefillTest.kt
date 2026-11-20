package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlayQueueRefillTest {
    private fun solo(
        names: String,
        seed: Long = 9L,
    ) = PlayQueue(ReverseShuffle, Random(1)).apply {
        replace(refs(names), 0, PlaySource.SEARCH, false, soloSeed = seed)
    }

    @Test
    fun `append goes after the manual entries at the tail`() {
        val queue = solo("AB")
        queue.addToQueue(refs("Z"))

        queue.append(refs("XY"), PlaySource.RADIO, RefillSeed.Song(9))

        assertEquals("AZBXY", queue.names())
    }

    @Test
    fun `appended entries are non-manual, carry the seed and the batch source`() {
        val queue = solo("A")

        queue.append(refs("XY"), PlaySource.RADIO, RefillSeed.Song(9))

        val appended = queue.snapshot().entries.drop(1)
        assertEquals(listOf(PlaySource.RADIO, PlaySource.RADIO), appended.map { it.source })
        assertEquals(listOf(false, false), appended.map { it.manual })
        assertEquals(listOf<Long?>(9L, 9L), appended.map { it.soloSeed })
        assertEquals(listOf<Long?>(null, null), appended.map { it.mixId })
    }

    @Test
    fun `a mix batch carries the mix id`() {
        val queue = PlayQueue(ReverseShuffle, Random(1))
        queue.replace(refs("A"), 0, PlaySource.MIX, false, mixId = 4L)

        queue.append(refs("XY"), PlaySource.MIX, RefillSeed.Mix(4))

        assertEquals(listOf<Long?>(4L, 4L, 4L), queue.snapshot().entries.map { it.mixId })
    }

    @Test
    fun `append extends the original order so shuffle off restores it`() {
        val queue = solo("AB")
        queue.append(refs("CDE"), PlaySource.RADIO, RefillSeed.Song(9))
        queue.setShuffled(true)

        queue.setShuffled(false)

        assertEquals("ABCDE", queue.names())
    }

    @Test
    fun `append does not move the current song or the epoch`() {
        val queue = solo("AB")
        val before = queue.epoch

        queue.append(refs("C"), PlaySource.RADIO, RefillSeed.Song(9))

        assertEquals('A', queue.current?.name)
        assertEquals(before, queue.epoch)
    }

    @Test
    fun `the epoch moves on replace and restore`() {
        val queue = solo("AB")
        val first = queue.epoch

        queue.replace(refs("CD"), 0, PlaySource.ALBUM, false)
        val second = queue.epoch
        queue.restore(queue.snapshot())

        assertEquals(first + 1, second)
        assertEquals(second + 1, queue.epoch)
    }

    @Test
    fun `there is no signal for a queue without a seed or an empty one`() {
        assertNull(queue("ABC").refillSignal())
        assertNull(PlayQueue(ReverseShuffle, Random(1)).refillSignal())
    }

    @Test
    fun `there is no signal while repeat is not off and it comes back with off`() {
        val queue = solo("ABC")

        queue.repeatMode = RepeatMode.ALL
        assertNull(queue.refillSignal())
        queue.repeatMode = RepeatMode.ONE
        assertNull(queue.refillSignal())
        queue.repeatMode = RepeatMode.OFF
        assertNotNull(queue.refillSignal())
    }

    @Test
    fun `the signal counts only non-manual entries ahead`() {
        val queue = solo("ABCD")
        queue.playNext(refs("Z"))

        val signal = queue.refillSignal()

        assertEquals(3, signal?.nonManualAhead)
        assertEquals(RefillSeed.Song(9), signal?.seed)
    }

    @Test
    fun `a manual song playing with no auto songs left still gives the seed`() {
        val queue = solo("AB")
        queue.jumpTo(1)
        queue.addToQueue(refs("Z"))
        queue.jumpTo(2)

        val signal = queue.refillSignal()

        assertEquals(RefillSeed.Song(9), signal?.seed)
        assertEquals(0, signal?.nonManualAhead)
    }

    @Test
    fun `the signal lists every queued song, played ones included`() {
        val queue = solo("ABC")
        queue.jumpTo(2)

        assertEquals(refs("ABC"), queue.refillSignal()?.queued)
    }

    @Test
    fun `the signal carries the last four artists, oldest first`() {
        val songs = (1L..6L).map { SongRef(SongId(it), ArtistId(it * 10)) }
        val queue = PlayQueue(ReverseShuffle, Random(1))
        queue.replace(songs, 0, PlaySource.SEARCH, false, soloSeed = 1L)

        assertEquals(listOf(30L, 40L, 50L, 60L).map(::ArtistId), queue.refillSignal()?.lastArtists)
    }

    @Test
    fun `the signal carries the epoch`() {
        val queue = solo("AB")

        assertEquals(queue.epoch, queue.refillSignal()?.epoch)
    }

    @Test
    fun `replace keeps pending manual entries and drops the old auto songs and their seed`() {
        val queue = solo("ABC")
        queue.playNext(refs("Z"))

        queue.replace(refs("XY"), 0, PlaySource.SEARCH, false, soloSeed = 2L)

        assertEquals("XZY", queue.names())
        assertEquals(listOf<Long?>(2L, null, 2L), queue.snapshot().entries.map { it.soloSeed })
    }
}
