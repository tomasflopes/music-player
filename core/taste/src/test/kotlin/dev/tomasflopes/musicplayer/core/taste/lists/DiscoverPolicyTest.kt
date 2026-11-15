package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.taste.config.DiscoverConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DiscoverPolicyTest {
    private fun by(
        id: Long,
        artist: Long,
    ): Song = song(id, 0).copy(artistId = ArtistId(artist))

    /** Songs 1..[count], each by an artist of its own (artist id = song id). */
    private fun soloists(count: Int) = (1..count).map { by(it.toLong(), it.toLong()) }

    private fun scores(vararg pairs: Pair<Long, Double>) = pairs.associate { ArtistId(it.first) to it.second }

    private fun pick(
        library: List<Song>,
        played: Set<Long> = emptySet(),
        artistScores: Map<ArtistId, Double> = emptyMap(),
        config: DiscoverConfig = DiscoverConfig(),
        seed: Int = 1,
    ): List<Song> =
        DiscoverPolicy(config).pick(library, played.mapTo(HashSet()) { SongId(it) }, artistScores, Random(seed))

    private fun List<Song>.ids() = map { it.id.value }

    @Test
    fun `only songs with no log row are candidates`() {
        val picked = pick(soloists(5), played = setOf(2, 4))

        assertEquals(listOf(1L, 3L, 5L), picked.ids().sorted())
    }

    @Test
    fun `a library that was played in full gives an empty list`() {
        assertEquals(emptyList<Song>(), pick(soloists(3), played = setOf(1, 2, 3)))
    }

    @Test
    fun `an empty library gives an empty list`() {
        assertEquals(emptyList<Song>(), pick(emptyList()))
    }

    @Test
    fun `fewer candidates than the list size gives all of them`() {
        val picked = pick(soloists(4))

        assertEquals(listOf(1L, 2L, 3L, 4L), picked.ids().sorted())
    }

    @Test
    fun `the list is never longer than the size`() {
        assertEquals(50, pick(soloists(200)).size)
        assertEquals(10, pick(soloists(200), config = DiscoverConfig(size = 10)).size)
    }

    @Test
    fun `one artist cannot have more than the cap`() {
        val library = (1L..10L).map { by(it, artist = 1) } + (11L..20L).map { by(it, artist = 2) }

        val picked = pick(library)

        assertEquals(6, picked.size)
        assertEquals(3, picked.count { it.artistId == ArtistId(1) })
        assertEquals(3, picked.count { it.artistId == ArtistId(2) })
    }

    @Test
    fun `the cap is hard, so a one-artist library gives a short list`() {
        val picked = pick((1L..10L).map { by(it, artist = 1) }, config = DiscoverConfig(maxPerArtist = 2))

        assertEquals(2, picked.size)
    }

    @Test
    fun `the cap keeps the best songs of an artist and makes room for the next artist`() {
        val library = (1L..5L).map { by(it, artist = 1) } + by(6, artist = 2) + by(7, artist = 3)
        val noRandom = DiscoverConfig(randomWeight = 0.0, artistGap = 0)

        val picked = pick(library, artistScores = scores(1L to 5.0), config = noRandom)

        assertEquals(listOf(1L, 2L, 3L, 6L, 7L), picked.ids())
    }

    @Test
    fun `artists with a higher score come first, unknown artists in between`() {
        val noRandom = DiscoverConfig(randomWeight = 0.0)

        val picked = pick(soloists(5), artistScores = scores(3L to 8.0, 5L to 4.0, 1L to -4.0), config = noRandom)

        assertEquals(listOf(3L, 5L, 2L, 4L, 1L), picked.ids())
    }

    @Test
    fun `a liked artist beats the random part whatever the seed`() {
        val library = soloists(30)

        repeat(50) { seed ->
            val picked = pick(library, artistScores = scores(17L to 20.0), seed = seed)

            assertEquals(17L, picked.first().id.value)
        }
    }

    @Test
    fun `a disliked artist comes last but is not removed`() {
        val picked = pick(soloists(10), artistScores = scores(4L to -20.0))

        assertEquals(4L, picked.last().id.value)
        assertEquals(10, picked.size)
    }

    @Test
    fun `the same seed gives the same list and another seed another one`() {
        val library = soloists(200)

        assertEquals(pick(library, seed = 7), pick(library, seed = 7))
        assertNotEquals(pick(library, seed = 7).ids(), pick(library, seed = 8).ids())
    }

    @Test
    fun `the list does not depend on the order the library comes in`() {
        val library = soloists(100)

        assertEquals(pick(library).ids(), pick(library.reversed()).ids())
    }

    @Test
    fun `a song that gets played leaves and the others keep their order`() {
        val library = soloists(30)
        val before = pick(library)

        val after = pick(library, played = setOf(before.first().id.value))

        assertEquals(before.drop(1), after)
    }

    @Test
    fun `no artist repeats inside the gap when the list can avoid it`() {
        val library = (0L until 10L).flatMap { artist -> (1L..3L).map { by(artist * 3 + it, artist) } }
        val noRandom = DiscoverConfig(randomWeight = 0.0)

        val artists = pick(library, config = noRandom).map { it.artistId }

        assertEquals(30, artists.size)
        artists.forEachIndexed { index, artist ->
            val next = artists.drop(index + 1).take(noRandom.artistGap)
            assertTrue(artist !in next, "artist $artist repeats within ${noRandom.artistGap} songs at $index")
        }
    }

    @Test
    fun `the gap relaxes when there are too few artists`() {
        val library = (1L..3L).map { by(it, artist = 1) } + (4L..6L).map { by(it, artist = 2) }

        val picked = pick(library, config = DiscoverConfig(randomWeight = 0.0))

        assertEquals(listOf(1L, 4L, 2L, 5L, 3L, 6L), picked.ids())
    }

    @Test
    fun `a gap of zero keeps the rank order`() {
        val library = listOf(by(1, 1), by(2, 1), by(3, 2))

        val picked = pick(library, config = DiscoverConfig(randomWeight = 0.0, artistGap = 0))

        assertEquals(listOf(1L, 2L, 3L), picked.ids())
    }
}
