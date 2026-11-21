package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.DiscoverConfig
import dev.tomasflopes.musicplayer.core.taste.score.SongScore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * The sound space in these tests has one dimension, so a song's sound is one number and "close" is easy to read.
 * Anchors are liked songs that were played (they have a song score); every other song is a Discover candidate.
 */
class SoundAwareDiscoverPolicyTest {
    private fun by(
        id: Long,
        artist: Long,
    ): Song = song(id, 0).copy(artistId = ArtistId(artist))

    /** Songs 1..[count], each by an artist of its own (artist id = song id). */
    private fun soloists(count: Int) = (1..count).map { by(it.toLong(), it.toLong()) }

    private fun scores(vararg pairs: Pair<Long, Double>) = pairs.associate { ArtistId(it.first) to it.second }

    /** No random part and no exploration, so the order is the rank and nothing else. */
    private val plain = DiscoverConfig(soundRandomWeight = 0.0, explorationShare = 0.0, randomWeight = 0.0)

    private fun List<Song>.ids() = map { it.id.value }

    /**
     * @param points the sound of candidates and anchors that have a profile (song id to one number).
     * @param liked played songs the owner likes, best score first, with their sound.
     */
    private fun pick(
        candidates: List<Song>,
        points: Map<Long, Float> = emptyMap(),
        liked: List<Pair<Long, Float>> = emptyList(),
        artistScores: Map<ArtistId, Double> = emptyMap(),
        config: DiscoverConfig = plain,
        seed: Int = 1,
    ): List<Song> {
        val likedSongs = liked.map { (id, _) -> by(id, artist = 900 + id) }
        val sound = points + liked.toMap()
        val ids = sound.keys.sorted().toLongArray()
        val space = SoundSpace(ids, 1, FloatArray(ids.size) { sound.getValue(ids[it]) })
        val input =
            DiscoverInput(
                library = candidates + likedSongs,
                played = liked.mapTo(HashSet()) { SongId(it.first) },
                artistScores = artistScores,
                songScores = liked.mapIndexed { index, (id, _) -> SongScore(SongId(id), 100.0 - index, 1L) },
                space = space,
            )
        return SoundAwareDiscoverPolicy(config).pick(input, Random(seed))
    }

    @Test
    fun `an unscored artist whose song sounds like an anchor is lifted`() {
        val candidates = soloists(3)
        val points = mapOf(1L to 90f, 2L to 0.5f, 3L to 60f)

        val picked = pick(candidates, points, liked = listOf(500L to 0f))

        assertEquals(listOf(2L, 3L, 1L), picked.ids())
    }

    @Test
    fun `the lift holds whatever the random part draws`() {
        val points = mapOf(1L to 90f, 2L to 0.5f, 3L to 60f)
        val config = DiscoverConfig(explorationShare = 0.0)

        repeat(50) { seed ->
            val picked = pick(soloists(3), points, liked = listOf(500L to 0f), config = config, seed = seed)

            assertEquals(2L, picked.first().id.value, "seed $seed")
        }
    }

    @Test
    fun `a liked artist still leads whatever the seed`() {
        // Every candidate sits at the same distance from the anchors, so the sound term is the same for all.
        val points = (1L..30L).associateWith { 50f }
        val config = DiscoverConfig(explorationShare = 0.0)

        repeat(50) { seed ->
            val liked = listOf(500L to 0f, 501L to 100f)

            val picked = pick(soloists(30), points, liked, scores(17L to 20.0), config, seed)

            assertEquals(17L, picked.first().id.value, "seed $seed")
        }
    }

    @Test
    fun `a liked artist is not buried by a song that sounds like an anchor`() {
        val points = mapOf(1L to 0.5f, 2L to 50f)

        val picked = pick(soloists(2), points, liked = listOf(500L to 0f), artistScores = scores(2L to 20.0))

        assertEquals(listOf(2L, 1L), picked.ids())
    }

    @Test
    fun `a song with no sound profile ranks by its artist alone, like the old policy`() {
        val candidates = soloists(5)
        val artistScores = scores(3L to 8.0, 5L to 4.0, 1L to -4.0)
        val old = DiscoverConfig(randomWeight = 0.0)

        val picked = pick(candidates, points = emptyMap(), liked = listOf(500L to 0f), artistScores = artistScores)

        assertEquals(listOf(3L, 5L, 2L, 4L, 1L), picked.ids())
        assertEquals(
            DiscoverPolicy(old).pick(candidates, emptySet(), artistScores, Random(1)).ids(),
            picked.ids(),
        )
    }

    @Test
    fun `a song with no profile is not buried below one that sounds far from every anchor`() {
        // Song 1 has a profile and is far from both anchors; song 2 has none. Reach is the second nearest song.
        val points = mapOf(1L to 100f)
        val config = plain.copy(densityRank = 2)

        val picked = pick(soloists(2), points, liked = listOf(500L to 0f, 501L to 1f), config = config)

        assertEquals(listOf(2L, 1L), picked.ids())
    }

    @Test
    fun `the nearest anchor decides, not the middle between anchors`() {
        val points = mapOf(1L to 1f, 2L to 50f, 3L to 99f)

        val picked = pick(soloists(3), points, liked = listOf(500L to 0f, 501L to 100f))

        assertEquals(2L, picked.last().id.value)
    }

    @Test
    fun `anchors are spread over different sounds, not only the top scorers`() {
        // Ten liked songs share one sound and score high; one liked song with another sound scores lowest.
        val liked = (0 until 10).map { (500L + it) to 0f } + (600L to 100f)
        val points = mapOf(1L to 99f, 2L to 50f)
        val config = plain.copy(anchorCount = 2)

        val picked = pick(soloists(2), points, liked, config = config)

        assertEquals(1L, picked.first().id.value)
    }

    @Test
    fun `an empty anchor set gives the old list`() {
        val library = soloists(60)
        val artistScores = scores(7L to 9.0, 21L to 3.0)
        val config = DiscoverConfig()

        val picked =
            SoundAwareDiscoverPolicy(config).pick(
                DiscoverInput(
                    library,
                    emptySet(),
                    artistScores,
                    songScores = emptyList(),
                    space = SoundSpace(longArrayOf(1), 1, floatArrayOf(0f)),
                ),
                Random(5),
            )

        assertEquals(DiscoverPolicy(config).pick(library, emptySet(), artistScores, Random(5)), picked)
    }

    @Test
    fun `a missing sound space gives the old list`() {
        val library = soloists(60)
        val config = DiscoverConfig()

        val picked =
            SoundAwareDiscoverPolicy(config).pick(
                DiscoverInput(
                    library,
                    emptySet(),
                    emptyMap(),
                    songScores = listOf(SongScore(SongId(1), 5.0, 1L)),
                    space = null,
                ),
                Random(5),
            )

        assertEquals(DiscoverPolicy(config).pick(library, emptySet(), emptyMap(), Random(5)), picked)
    }

    @Test
    fun `only songs with no log row are candidates`() {
        val points = (1L..5L).associateWith { it.toFloat() }

        val picked = pick(soloists(5), points, liked = listOf(500L to 0f))

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), picked.ids().sorted())
        assertTrue(picked.none { it.id.value == 500L })
    }

    @Test
    fun `one artist cannot have more than the cap and the list stays at most the size`() {
        val library = (1L..40L).map { by(it, artist = it % 4) }
        val points = (1L..40L).associateWith { it.toFloat() }
        val config = DiscoverConfig(size = 10, explorationShare = 0.2)

        val picked = pick(library, points, liked = listOf(500L to 0f), config = config)

        assertTrue(picked.size <= 10)
        assertTrue(picked.groupBy { it.artistId }.values.all { it.size <= config.maxPerArtist })
    }

    @Test
    fun `no artist repeats inside the gap when the list can avoid it`() {
        val library = (0L until 10L).flatMap { artist -> (1L..3L).map { by(artist * 3 + it, artist) } }
        val points = library.associate { it.id.value to it.id.value.toFloat() }

        val artists = pick(library, points, liked = listOf(500L to 0f)).map { it.artistId }

        assertEquals(30, artists.size)
        artists.forEachIndexed { index, artist ->
            val next = artists.drop(index + 1).take(plain.artistGap)
            assertTrue(artist !in next, "artist $artist repeats within ${plain.artistGap} songs at $index")
        }
    }

    @Test
    fun `the same seed gives the same list and another seed another one`() {
        val library = soloists(200)
        val points = library.associate { it.id.value to (it.id.value % 37).toFloat() }
        val config = DiscoverConfig()
        val liked = listOf(500L to 0f, 501L to 30f)

        assertEquals(
            pick(library, points, liked, config = config, seed = 7),
            pick(library, points, liked, config = config, seed = 7),
        )
        assertNotEquals(
            pick(library, points, liked, config = config, seed = 7).ids(),
            pick(library, points, liked, config = config, seed = 8).ids(),
        )
    }

    @Test
    fun `exploration songs come only from the nearest band`() {
        val library = soloists(100)
        val points = library.associate { it.id.value to it.id.value.toFloat() }
        val config = plain.copy(size = 10, explorationShare = 0.2, explorationBand = 20, maxPerArtist = 3)
        var someoneBeyondTheNearestTen = false

        repeat(20) { seed ->
            val picked = pick(library, points, liked = listOf(500L to 0f), config = config, seed = seed)

            assertEquals(10, picked.size, "seed $seed")
            assertTrue(picked.all { it.id.value <= 20 }, "seed $seed: ${picked.ids()}")
            if (picked.any { it.id.value > 10 }) someoneBeyondTheNearestTen = true
        }
        assertTrue(someoneBeyondTheNearestTen)
    }

    @Test
    fun `a library with no candidates gives an empty list`() {
        assertEquals(emptyList<Song>(), pick(emptyList(), liked = listOf(500L to 0f)))
    }
}
