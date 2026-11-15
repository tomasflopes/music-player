package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.taste.config.DiscoverConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DuplicateKeyTest {
    private fun copy(
        id: Long,
        artist: String,
        title: String,
        artistId: Long = artist.hashCode().toLong(),
    ): Song =
        song(id, 0).copy(title = title, artistName = artist, tagArtistName = artist, artistId = ArtistId(artistId))

    @Test
    fun `case, accents and punctuation do not matter`() {
        val a = copy(1, "Ferry", "Isso é")
        val b = copy(2, "FERRY", "isso e!")
        val c = copy(3, "Ferry.", "  Isso   é ")

        assertEquals(a.duplicateKey(), b.duplicateKey())
        assertEquals(a.duplicateKey(), c.duplicateKey())
    }

    @Test
    fun `the main artist is part of the key`() {
        assertNotEquals(copy(1, "Dillaz", "Copo").duplicateKey(), copy(2, "Chill", "Copo").duplicateKey())
    }

    @Test
    fun `the title is part of the key`() {
        assertNotEquals(copy(1, "Dillaz", "Copo").duplicateKey(), copy(2, "Dillaz", "Copo 2").duplicateKey())
    }

    @Test
    fun `the artist and the title do not run into each other`() {
        assertNotEquals(copy(1, "ab", "c").duplicateKey(), copy(2, "a", "bc").duplicateKey())
    }

    @Test
    fun `a copy of a played song is not a candidate`() {
        val library = listOf(copy(1, "Dillaz", "Copo"), copy(2, "Dillaz", "Copo"), copy(3, "Valete", "Fim"))

        val candidates = library.neverPlayed(setOf(SongId(2)))

        assertEquals(listOf(3L), candidates.map { it.id.value })
    }

    @Test
    fun `only the copy with the lowest song id is a candidate`() {
        val library = listOf(copy(9, "Dillaz", "Copo"), copy(4, "Dillaz", "Copo"), copy(7, "Dillaz", "Copo"))

        assertEquals(listOf(4L), library.neverPlayed(emptySet()).map { it.id.value })
    }

    @Test
    fun `a played song that left the library still hides its copy`() {
        val library = listOf(copy(2, "Dillaz", "Copo"))
        val retiredCopy = copy(1, "Dillaz", "Copo")

        // The played id (1) is not in the library list; only library songs can name a key, so the copy stays.
        assertEquals(listOf(2L), library.neverPlayed(setOf(retiredCopy.id)).map { it.id.value })
    }

    @Test
    fun `discover lists one copy of a song and none of a played one`() {
        val library =
            listOf(
                copy(1, "Dillaz", "Copo", artistId = 1),
                copy(2, "Dillaz", "Copo", artistId = 1),
                copy(3, "Valete", "Fim", artistId = 2),
                copy(4, "Valete", "Fim", artistId = 2),
                copy(5, "Plutonio", "Bilhete", artistId = 3),
                copy(6, "Plutonio", "Bilhete", artistId = 3),
            )

        val list = DiscoverPolicy(DiscoverConfig()).pick(library, setOf(SongId(6)), emptyMap(), Random(1))

        assertEquals(listOf(1L, 3L), list.map { it.id.value }.sorted())
    }
}
