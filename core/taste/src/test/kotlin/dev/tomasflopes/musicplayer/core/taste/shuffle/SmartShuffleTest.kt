package dev.tomasflopes.musicplayer.core.taste.shuffle

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.taste.config.SmartShuffleConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SmartShuffleTest : ShuffleStrategyContract() {
    override fun strategy(): ShuffleStrategy = SmartShuffle()

    private val minDistance = SmartShuffleConfig().artistGap + 1

    private fun song(
        id: Int,
        artist: Int,
    ) = SongRef(SongId(id.toLong()), ArtistId(artist.toLong()))

    /** One artist per entry of [sizes], with that many songs. */
    private fun library(sizes: List<Int>): List<SongRef> {
        var id = 0
        return sizes.flatMapIndexed { artist, size -> List(size) { song(id++, artist) } }
    }

    /** The smallest distance between two songs of one artist; Int.MAX_VALUE when no artist repeats. */
    private fun closest(order: List<SongRef>): Int {
        val last = HashMap<ArtistId, Int>()
        var best = Int.MAX_VALUE
        order.forEachIndexed { i, s -> last.put(s.mainArtistId, i)?.let { best = minOf(best, i - it) } }
        return best
    }

    private fun assertPermutation(
        items: List<SongRef>,
        order: List<SongRef>,
    ) {
        assertEquals(items.size, order.size)
        assertEquals(items.toSet(), order.toSet())
    }

    @Test
    fun `result is a permutation, nothing dropped or duplicated`() {
        val items = library(List(40) { 1 + it % 9 })

        assertPermutation(items, strategy().order(items, Random(3)))
    }

    @Test
    fun `with enough artists no two songs of one artist are closer than the gap`() {
        val equal = library(List(40) { 8 })
        val uneven = library(List(60) { 1 + it % 10 })

        for (seed in 1..100) {
            assertTrue(closest(strategy().order(equal, Random(seed))) >= minDistance, "equal, seed $seed")
            assertTrue(closest(strategy().order(uneven, Random(seed))) >= minDistance, "uneven, seed $seed")
        }
    }

    @Test
    fun `different seeds still give different orders`() {
        val items = library(List(40) { 8 })

        assertTrue(strategy().order(items, Random(1)) != strategy().order(items, Random(2)))
    }

    @Test
    fun `too few artists relaxes the gap to the best possible, no crash`() {
        val items = library(listOf(10, 10, 10))

        for (seed in 1..20) {
            val order = strategy().order(items, Random(seed))
            assertPermutation(items, order)
            assertTrue(closest(order) >= 3, "three artists can be spaced by 2 tracks, seed $seed")
        }
    }

    @Test
    fun `one artist dominating is best effort`() {
        val items = library(listOf(50) + List(10) { 1 })

        for (seed in 1..20) assertPermutation(items, strategy().order(items, Random(seed)))
    }

    @Test
    fun `a dominating artist that can still be spaced is spaced`() {
        val items = library(listOf(10) + List(40) { 1 })

        for (seed in 1..20) {
            assertTrue(closest(strategy().order(items, Random(seed))) >= minDistance, "seed $seed")
        }
    }

    @Test
    fun `a single artist returns all songs`() {
        val items = library(listOf(30))

        assertPermutation(items, strategy().order(items, Random(1)))
    }

    @Test
    fun `songs that play right after the current one keep clear of its artist`() {
        val current = song(-1, 0)
        val items = library(List(20) { 5 })

        for (seed in 1..100) {
            val order = SmartShuffle().orderAfter(listOf(current), items, Random(seed))

            assertPermutation(items, order)
            assertTrue(closest(listOf(current) + order) >= minDistance, "seed $seed")
        }
    }

    @Test
    fun `several preceding songs all count`() {
        val preceding = listOf(song(-3, 0), song(-2, 1), song(-1, 2))
        val items = library(List(20) { 5 })

        for (seed in 1..100) {
            val order = SmartShuffle().orderAfter(preceding, items, Random(seed))

            assertTrue(closest(preceding + order) >= minDistance, "seed $seed")
        }
    }

    @Test
    fun `a gap of 0 is plain shuffle`() {
        val items = library(List(5) { 6 })

        assertEquals(
            items.shuffled(Random(9)).toSet(),
            SmartShuffle(SmartShuffleConfig(artistGap = 0)).order(items, Random(9)).toSet(),
        )
    }

    @Test
    fun `the config refuses a negative or huge gap`() {
        assertThrows(IllegalArgumentException::class.java) { SmartShuffleConfig(artistGap = -1) }
        assertThrows(IllegalArgumentException::class.java) {
            SmartShuffleConfig(artistGap = SmartShuffleConfig.MAX_ARTIST_GAP + 1)
        }
    }

    @Test
    fun `16000 songs by 1500 artists`() {
        val assign = Random(7)
        val items = List(16_000) { song(it, assign.nextInt(1_500)) }
        repeat(5) { strategy().order(items, Random(it)) }

        val times =
            (0 until 15).map {
                val start = System.nanoTime()
                val order = strategy().order(items, Random(100 + it))
                val ms = (System.nanoTime() - start) / 1_000_000.0
                assertTrue(closest(order) >= minDistance)
                ms
            }
        val median = times.sorted()[times.size / 2]
        println("smart shuffle 16000 songs, 1500 artists: median $median ms")

        assertTrue(median < 250, "median $median ms")
    }
}
