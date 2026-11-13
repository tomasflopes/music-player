package dev.tomasflopes.musicplayer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AlbumOrderTest {
    private fun assertOrder(
        expectedFileNames: List<String>,
        vararg songs: Song,
    ) {
        assertEquals(expectedFileNames, songs.sortedWith(AlbumOrder).map { it.fileName })
    }

    @Test
    fun `disc then track`() {
        assertOrder(
            listOf("c", "b", "a"),
            song("a", disc = 2, track = 1),
            song("b", disc = 1, track = 2),
            song("c", disc = 1, track = 1),
        )
    }

    @Test
    fun `missing disc counts as disc 1`() {
        assertOrder(listOf("a", "b"), song("b", disc = 2, track = 1), song("a", disc = null, track = 9))
    }

    @Test
    fun `no numbers falls back to natural filename order`() {
        assertOrder(
            listOf("1 - x.mp3", "2 - y.mp3", "10 - z.mp3"),
            song("10 - z.mp3"),
            song("2 - y.mp3"),
            song("1 - x.mp3"),
        )
    }

    @Test
    fun `numbered tracks come before unnumbered ones`() {
        assertOrder(
            listOf("z", "y", "a", "b"),
            song("b"),
            song("y", track = 2),
            song("a"),
            song("z", track = 1),
        )
    }

    @Test
    fun `unnumbered tracks stay on their own disc`() {
        assertOrder(
            listOf("d1 t1", "d1 none", "d2 t1"),
            song("d2 t1", disc = 2, track = 1),
            song("d1 none", disc = 1),
            song("d1 t1", disc = 1, track = 1),
        )
    }

    @Test
    fun `duplicate track numbers order by filename and are stable across shuffled input`() {
        val songs =
            listOf(
                song("b", track = 1),
                song("a", track = 1),
                song("c", track = 2),
                song("a", track = 1, path = "Music/Other/a"),
                song("A", track = 1),
            )

        val orders = (1..20).map { seed -> songs.shuffled(Random(seed)).sortedWith(AlbumOrder).map { it.path } }

        assertEquals(1, orders.toSet().size, "different orders: ${orders.toSet()}")
        assertEquals(listOf("A", "a", "a", "b", "c"), songs.sortedWith(AlbumOrder).map { it.fileName })
    }
}
