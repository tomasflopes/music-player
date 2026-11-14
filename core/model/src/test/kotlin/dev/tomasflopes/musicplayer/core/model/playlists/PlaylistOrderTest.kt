package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.SongId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaylistOrderTest {
    private val a = SongId(1)
    private val b = SongId(2)
    private val c = SongId(3)

    @Test
    fun `songs take the places the visible songs held, in the new order`() {
        val current = listOf(a to 0, b to 1, c to 2)

        val moves = PlaylistOrder.reassign(current, listOf(c, a, b))

        assertEquals(listOf(c to 0, a to 1, b to 2), moves)
    }

    @Test
    fun `a gap left by a hidden row stays where it is`() {
        // Position 1 belongs to a retired song that the reorder never sees.
        val current = listOf(a to 0, b to 2, c to 3)

        val moves = PlaylistOrder.reassign(current, listOf(b, c, a))

        assertEquals(listOf(b to 0, c to 2, a to 3), moves)
    }

    @Test
    fun `an unchanged order changes nothing`() {
        val current = listOf(a to 0, b to 4)

        assertEquals(current, PlaylistOrder.reassign(current, listOf(a, b)))
    }

    @Test
    fun `an order with a missing, extra or repeated song is refused`() {
        val current = listOf(a to 0, b to 1)

        assertNull(PlaylistOrder.reassign(current, listOf(a)))
        assertNull(PlaylistOrder.reassign(current, listOf(a, b, c)))
        assertNull(PlaylistOrder.reassign(current, listOf(a, a)))
        assertNull(PlaylistOrder.reassign(current, listOf(a, c)))
    }

    @Test
    fun `positions out of order in the input still go to the song order's places sorted`() {
        val current = listOf(a to 5, b to 3)

        assertEquals(listOf(b to 3, a to 5), PlaylistOrder.reassign(current, listOf(b, a)))
    }
}
