package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeedKind
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.lists.song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class MixEngineTest {
    /** A song by [artist] (default: an artist of its own), with a title and file name no other test song shares. */
    private fun by(
        id: Long,
        artist: Long = id,
    ): Song = song(id, 0).copy(artistId = ArtistId(artist), tagArtistName = "artist $artist")

    private fun line(vararg points: Pair<Long, Float>) =
        SoundSpace(points.map { it.first }.toLongArray(), 1, points.map { it.second }.toFloatArray())

    private fun ramp(count: Int) = line(*(1..count).map { it.toLong() to it.toFloat() }.toTypedArray())

    private fun songSeed(id: Long) = MixSeed(MixSeedKind.SONG, id)

    private val nearestOnly = MixConfig(explorationShare = 0.0, artistGap = 0, densityRank = 3, openingPerArtist = 0)

    private fun pick(
        seeds: List<MixSeed>,
        library: List<Song>,
        space: SoundSpace,
        recent: Set<Long> = emptySet(),
        config: MixConfig = MixConfig(),
        random: Int = 1,
    ): List<Song> =
        MixEngine(config).pick(seeds, library, space, recent.mapTo(HashSet()) { SongId(it) }, Random(random))

    private fun List<Song>.ids() = map { it.id.value }

    @Test
    fun `a batch has the configured size and no song twice`() {
        val library = (1L..100L).map { by(it) }

        val batch = pick(listOf(songSeed(1)), library, ramp(100))

        assertEquals(25, batch.size)
        assertEquals(25, batch.ids().toSet().size)
    }

    @Test
    fun `seeds without a point in the space give nothing`() {
        val library = (1L..10L).map { by(it) }

        assertTrue(pick(listOf(songSeed(999)), library, ramp(10)).isEmpty())
        assertTrue(pick(emptyList(), library, ramp(10)).isEmpty())
    }

    @Test
    fun `a seed song is in its own mix and the nearest songs follow`() {
        val library = (1L..10L).map { by(it) }

        val batch = pick(listOf(songSeed(1)), library, ramp(10), config = nearestOnly)

        assertEquals((1L..10L).toList(), batch.ids())
    }

    @Test
    fun `two seeds in different places pull both neighbourhoods`() {
        val library = (1L..60L).map { by(it) }

        val config = nearestOnly.copy(batchSize = 12, explorationBand = 12)

        val batch = pick(listOf(songSeed(5), songSeed(55)), library, ramp(60), config = config)

        assertTrue(batch.ids().count { it <= 10 } >= 4)
        assertTrue(batch.ids().count { it >= 50 } >= 4)
        assertTrue(batch.ids().none { it in 20L..40L })
    }

    @Test
    fun `an artist seed is the centroid of its profiled songs`() {
        // Artist 7 has songs 10 and 30 (centroid 20); the others are spread so song 20 is the only one on it.
        val library = (1L..40L).map { by(it, artist = if (it == 10L || it == 30L) 7 else it + 100) }

        val batch =
            pick(
                listOf(MixSeed(MixSeedKind.ARTIST, 7)),
                library,
                ramp(40),
                config = nearestOnly.copy(batchSize = 3, explorationBand = 3, refillLowWater = 1),
            )

        assertEquals(20L, batch.first().id.value)
    }

    @Test
    fun `every artist seed opens the batch with its own songs`() {
        // Each seed artist's two songs lie far apart, so their centroid sits among strangers' songs.
        val crowd = (1L..120L).map { by(it, artist = it + 1000) }
        val library = crowd + listOf(by(201, artist = 7), by(202, artist = 7), by(301, artist = 8), by(302, artist = 8))
        val points =
            (1L..60L).map { it to 500f + it * 0.01f } + (61L..120L).map { it to 2500f + it * 0.01f } +
                listOf(201L to 100f, 202L to 900f, 301L to 2100f, 302L to 2900f)

        val batch =
            pick(
                listOf(MixSeed(MixSeedKind.ARTIST, 7), MixSeed(MixSeedKind.ARTIST, 8)),
                library,
                line(*points.toTypedArray()),
                config = MixConfig(explorationShare = 0.0, artistGap = 0, densityRank = 3),
            )

        assertTrue(batch.ids().containsAll(listOf(201L, 202L, 301L, 302L)), "batch: ${batch.ids()}")
    }

    @Test
    fun `a song outside the library is never in the batch`() {
        val library = (1L..10L).map { by(it) }
        val space = ramp(20)

        val batch = pick(listOf(songSeed(1)), library, space, config = nearestOnly)

        assertTrue(batch.ids().all { it <= 10 })
    }

    @Test
    fun `a recently played song ranks later but stays`() {
        val library = (1L..10L).map { by(it) }

        val config = nearestOnly.copy(recentPenalty = 5.0)

        val batch = pick(listOf(songSeed(1)), library, ramp(10), recent = setOf(2L), config = config)

        assertEquals(10, batch.size)
        assertEquals(2L, batch.ids().last())
    }

    @Test
    fun `no more than the artist cap from one main artist`() {
        val library = (1L..30L).map { by(it, artist = 1) }

        val batch = pick(listOf(songSeed(1)), library, ramp(30), config = nearestOnly)

        assertEquals(3, batch.size)
    }

    @Test
    fun `exploration comes only from the band of nearest songs`() {
        val library = (1L..2000L).map { by(it) }
        val config = MixConfig(explorationShare = 0.3, explorationBand = 100, artistGap = 0, densityRank = 3)

        (1..20).forEach { random ->
            val batch = pick(listOf(songSeed(1)), library, ramp(2000), config = config, random = random)
            assertTrue(batch.ids().all { it <= 100 }, "random $random: ${batch.ids()}")
        }
    }

    @Test
    fun `one random draw gives one batch`() {
        val library = (1L..300L).map { by(it) }

        assertEquals(
            pick(listOf(songSeed(1)), library, ramp(300), random = 5).ids(),
            pick(listOf(songSeed(1)), library, ramp(300), random = 5).ids(),
        )
    }

    private fun refill(
        seeds: List<MixSeed>,
        library: List<Song>,
        space: SoundSpace,
        exclude: List<Song> = emptyList(),
        config: MixConfig = nearestOnly,
    ): List<Song> = MixEngine(config).refill(seeds, library, space, emptySet(), Random(1), exclude, emptyList())

    @Test
    fun `a refill never picks a queued song and still fills a whole batch`() {
        val library = (1L..100L).map { by(it) }
        val queued = library.take(25)

        val batch = refill(listOf(songSeed(1)), library, ramp(100), exclude = queued)

        assertEquals(25, batch.size)
        assertTrue(batch.ids().none { it in 1L..25L })
    }

    @Test
    fun `a refill has no opening songs`() {
        // The artist's two songs lie far apart, so its centroid sits among the other songs.
        val library = (1L..40L).map { by(it, artist = if (it <= 2) 500 else it) }
        val space =
            line(
                *(1..40)
                    .map {
                        it.toLong() to (
                            if (it == 1) {
                                0f
                            } else if (it == 2) {
                                100f
                            } else {
                                31f + it
                            }
                        )
                    }.toTypedArray(),
            )
        val artistSeed = MixSeed(MixSeedKind.ARTIST, 500)
        val config = MixConfig(openingPerArtist = 2, explorationShare = 0.0, artistGap = 0, densityRank = 3)

        val picked = MixEngine(config).pick(listOf(artistSeed), library, space, emptySet(), Random(1))
        val refilled = refill(listOf(artistSeed), library, space, config = config)

        assertTrue(picked.ids().take(2).all { it <= 2L })
        assertTrue(refilled.ids().none { it <= 2L })
    }
}
