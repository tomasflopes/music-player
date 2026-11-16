package dev.tomasflopes.musicplayer.core.model.sound

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundSpaceTest {
    /** Four songs on a line: 10 at 0, 20 at 1, 30 at 3, 40 at 6 (dim 1), plus a second axis that is all zero. */
    private fun line() =
        SoundSpace(
            songIds = longArrayOf(10, 20, 30, 40),
            dim = 2,
            values = floatArrayOf(0f, 0f, 1f, 0f, 3f, 0f, 6f, 0f),
        )

    @Test
    fun `the values must be dim floats per song`() {
        assertThrows(IllegalArgumentException::class.java) { SoundSpace(longArrayOf(1, 2), 2, FloatArray(3)) }
        assertThrows(IllegalArgumentException::class.java) { SoundSpace(longArrayOf(1), 0, FloatArray(0)) }
    }

    @Test
    fun `a song id finds its row and an unknown id finds none`() {
        val space = line()

        assertEquals(2, space.indexOf(30))
        assertEquals(-1, space.indexOf(99))
        assertEquals(4, space.size)
    }

    @Test
    fun `distance is plain euclidean`() {
        val space =
            SoundSpace(longArrayOf(1, 2), 3, floatArrayOf(0f, 0f, 0f, 1f, 2f, 2f))

        assertEquals(3.0, space.distance(0, 1), 1e-9)
        assertEquals(0.0, space.distance(1, 1), 1e-9)
    }

    @Test
    fun `nearest lists the closest songs first and leaves the seed out`() {
        val near = line().nearest(row = 0, k = 2)

        assertEquals(listOf(20L, 30L), near.map { it.songId })
        assertEquals(listOf(1.0, 3.0), near.map { it.distance })
    }

    @Test
    fun `nearest skips rows the caller does not accept`() {
        val space = line()

        val near = space.nearest(row = 0, k = 2) { row -> space.songIds[row] != 20L }

        assertEquals(listOf(30L, 40L), near.map { it.songId })
    }

    @Test
    fun `nearest breaks a tie by the lower song id`() {
        val space = SoundSpace(longArrayOf(5, 7, 9), 1, floatArrayOf(0f, 1f, -1f))

        assertEquals(listOf(7L, 9L), space.nearest(row = 0, k = 2).map { it.songId })
    }

    @Test
    fun `k larger than the space returns every other song`() {
        assertEquals(3, line().nearest(row = 1, k = 50).size)
        assertTrue(line().nearest(row = 1, k = 0).isEmpty())
    }

    @Test
    fun `the centroid is the mean of the rows, value by value`() {
        val space = SoundSpace(longArrayOf(1, 2, 3), 2, floatArrayOf(0f, 4f, 2f, 0f, 10f, 2f))

        val centre = space.centroid(listOf(0, 1))

        assertEquals(listOf(1.0, 2.0), centre.toList())
        assertEquals(listOf(4.0, 2.0), space.centroid(listOf(0, 1, 2)).toList())
    }

    @Test
    fun `the centroid of one row is that row`() {
        val space = line()

        assertEquals(space.vector(2).map { it.toDouble() }, space.centroid(listOf(2)).toList())
    }

    @Test
    fun `the centroid does not depend on the order of the rows`() {
        val space = line()

        assertEquals(space.centroid(listOf(0, 2, 3)).toList(), space.centroid(listOf(3, 0, 2)).toList())
    }

    @Test
    fun `the centroid of no rows is refused`() {
        assertThrows(IllegalArgumentException::class.java) { line().centroid(emptyList()) }
    }
}
