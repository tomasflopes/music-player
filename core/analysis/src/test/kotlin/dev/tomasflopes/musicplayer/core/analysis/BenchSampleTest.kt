package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BenchSampleTest {
    private val paths = (1..200).map { "Artist ${it % 7}/Album/%03d track.mp3".format(it) }

    @Test
    fun `the same seed picks the same songs whatever the input order`() {
        val first = BenchSample.pick(paths, 60)
        val second = BenchSample.pick(paths.reversed(), 60)

        assertEquals(60, first.size)
        assertEquals(first, second)
    }

    @Test
    fun `another seed picks other songs`() {
        assertNotEquals(BenchSample.pick(paths, 60, seed = 1L), BenchSample.pick(paths, 60, seed = 2L))
    }

    @Test
    fun `the pick is distinct, sorted and part of the input`() {
        val picked = BenchSample.pick(paths, 60)

        assertEquals(picked.distinct(), picked)
        assertEquals(picked.sorted(), picked)
        assertTrue(paths.containsAll(picked))
    }

    @Test
    fun `asking for more than exist returns all of them`() {
        assertEquals(paths.sorted(), BenchSample.pick(paths, 500))
    }

    @Test
    fun `decomposed and composed accents are the same song`() {
        val composed = "Beyoncé/Lemonade/01 track.mp3"
        val decomposed = "Beyoncé/Lemonade/01 track.mp3"

        assertEquals(listOf(composed), BenchSample.pick(listOf(decomposed, composed), 5))
    }
}
