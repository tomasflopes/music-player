package dev.tomasflopes.musicplayer.core.taste.shuffle

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Every [ShuffleStrategy] must pass these. A new strategy's test class extends this one. */
abstract class ShuffleStrategyContract {
    abstract fun strategy(): ShuffleStrategy

    protected fun refs(count: Int) = (0 until count).map { SongRef(SongId(it.toLong()), ArtistId((it % 50).toLong())) }

    @Test
    fun `returns every song exactly once`() {
        val items = refs(500)

        val order = strategy().order(items, Random(1))

        assertEquals(items.size, order.size)
        assertEquals(items.toSet(), order.toSet())
    }

    @Test
    fun `empty and single lists`() {
        assertEquals(emptyList<SongRef>(), strategy().order(emptyList(), Random(1)))
        assertEquals(refs(1), strategy().order(refs(1), Random(1)))
    }

    @Test
    fun `same seed, same order`() {
        val items = refs(200)

        assertEquals(strategy().order(items, Random(37)), strategy().order(items, Random(37)))
    }

    @Test
    fun `does not change the input list`() {
        val items = refs(100)
        val copy = items.toList()

        strategy().order(items, Random(2))

        assertEquals(copy, items)
    }

    @Test
    fun `16000 items under 50 ms`() {
        val items = refs(16_000)
        repeat(5) { strategy().order(items, Random(it)) }

        val start = System.nanoTime()
        strategy().order(items, Random(99))
        val ms = (System.nanoTime() - start) / 1_000_000

        assertTrue(ms < 50, "took $ms ms")
    }
}
