package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import dev.tomasflopes.musicplayer.core.taste.shuffle.BatchOrdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ArtistRadioPolicyTest {
    private val artist = ArtistId(1000)

    private fun by(
        id: Long,
        artist: Long = id,
    ): Song = song(id, 0).copy(artistId = ArtistId(artist), tagArtistName = "artist $artist")

    /** One-dimensional space: each pair is a song id and its position, so the distance is the position gap. */
    private fun line(vararg points: Pair<Long, Float>) =
        SoundSpace(points.map { it.first }.toLongArray(), 1, points.map { it.second }.toFloatArray())

    /** Songs 1..[count] at position = id. */
    private fun ramp(count: Int) = line(*(1..count).map { it.toLong() to it.toFloat() }.toTypedArray())

    private val nearestOnly = RadioConfig(nearestShare = 1.0, explorationShare = 0.0, artistGap = 0)

    private fun pick(
        library: List<Song>,
        space: SoundSpace,
        config: RadioConfig = nearestOnly,
        recent: Set<Long> = emptySet(),
        random: Int = 1,
    ): List<Song> =
        ArtistRadioPolicy(config, BatchOrdering.Identity)
            .pick(artist, library, space, recent.mapTo(HashSet()) { SongId(it) }, Random(random))

    private fun List<Song>.ids() = map { it.id.value }

    @Test
    fun `the query is the mean point of the artist's profiled songs`() {
        // The artist's songs 10 and 20 average to 15, a point that belongs to song 15 of another artist.
        val library = (1L..60L).map { if (it == 10L || it == 20L) by(it, artist = 1000) else by(it) }

        val batch = pick(library, ramp(60))

        // The artist's own songs open the batch (both 5 away), then the nearest to the point.
        assertEquals(listOf(10L, 20L, 15L, 14L, 16L, 13L, 17L), batch.ids().take(7))
    }

    @Test
    fun `the first song is the artist's and their closest songs come first even when far from the point`() {
        // The artist's songs sit at 0 and 100, the other songs between 30 and 87: the centre (50) is nearer to every
        // other song, so only the opening rule can put the artist's songs in the batch.
        val library = listOf(by(1, 1000), by(2, 1000)) + (3L..60L).map { by(it) }
        val space = line(1L to 0f, 2L to 100f, *(3L..60L).map { it to (it + 27).toFloat() }.toTypedArray())

        val batch = pick(library, space)

        assertEquals(listOf(1L, 2L), batch.ids().take(2))
        assertEquals(25, batch.size)
    }

    @Test
    fun `the first song stays the artist's after spacing and exploration`() {
        val library = (1L..10L).map { by(it, 1000) } + (11L..200L).map { by(it) }

        (1..10).forEach { random ->
            val batch = pick(library, ramp(200), config = RadioConfig(), random = random)

            assertEquals(artist, batch.first().artistId)
            assertEquals(3, batch.count { it.artistId == artist })
        }
    }

    @Test
    fun `an artist with no profiled song gives an empty batch`() {
        val library = listOf(by(1, 1000), by(2, 1000)) + (3L..30L).map { by(it) }
        val space = line(*(3L..30L).map { it to it.toFloat() }.toTypedArray())

        assertEquals(emptyList<Song>(), pick(library, space))
    }

    @Test
    fun `an artist that is not in the library gives an empty batch`() {
        val library = (1L..30L).map { by(it) }

        assertEquals(emptyList<Song>(), pick(library, ramp(30)))
    }

    @Test
    fun `an empty space gives an empty batch`() {
        val library = (1L..5L).map { by(it, 1000) }

        assertEquals(emptyList<Song>(), pick(library, SoundSpace(LongArray(0), 1, FloatArray(0))))
    }

    @Test
    fun `an artist with one profiled song uses that song's own point`() {
        val library = listOf(by(10, 1000), by(11, 1000)) + (1L..9L).map { by(it) } + (12L..40L).map { by(it) }
        // Song 11 has no profile, so only song 10 (position 10) defines the sound.
        val space = line(*((1L..10L) + (12L..40L)).map { it to it.toFloat() }.toTypedArray())

        val batch = pick(library, space)

        assertEquals(listOf(10L, 9L, 8L, 12L, 7L), batch.ids().take(5))
        assertTrue(11L !in batch.ids())
    }

    @Test
    fun `the artist's own songs can appear but never more than three`() {
        // Songs 1..10 are the artist's and sit at the centre of the sound, so every nearest pick would be theirs.
        val library = (1L..10L).map { by(it, 1000) } + (11L..60L).map { by(it) }

        val batch = pick(library, ramp(60))

        assertEquals(3, batch.count { it.artistId == artist })
        assertEquals(25, batch.size)
    }

    @Test
    fun `the batch is the configured size with no seed and no duplicates`() {
        val library = listOf(by(1, 1000)) + (2L..100L).map { by(it) }

        val batch = pick(library, ramp(100), config = RadioConfig())

        assertEquals(25, batch.size)
        assertEquals(25, batch.ids().toSet().size)
    }

    @Test
    fun `a recently played song ranks later but stays`() {
        val library = listOf(by(1, 1000)) + (2L..5L).map { by(it) }
        val space = line(1L to 0f, 2L to 0.05f, 3L to 0.1f, 4L to 0.12f, 5L to 5f)

        val plain = pick(library, space)
        val penalised = pick(library, space, recent = setOf(2))

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), plain.ids())
        assertEquals(listOf(1L, 3L, 4L, 2L, 5L), penalised.ids())
    }

    @Test
    fun `exploration comes only from the band of nearest songs`() {
        val library = listOf(by(1, 1000)) + (2L..200L).map { by(it) }
        val config = RadioConfig(nearestShare = 0.5, explorationShare = 0.5, explorationBand = 40, artistGap = 0)

        (1..20).forEach { random ->
            val batch = pick(library, ramp(200), config = config, random = random)

            assertTrue(batch.ids().all { it <= 40 }, "seed $random drew outside the band: ${batch.ids()}")
            assertEquals(25, batch.size)
        }
    }

    @Test
    fun `the same seed gives the same batch and another seed another one`() {
        val library = listOf(by(1, 1000)) + (2L..100L).map { by(it) }
        val config = RadioConfig(explorationBand = 100)

        val first = pick(library, ramp(100), config = config, random = 1).ids()

        assertEquals(first, pick(library, ramp(100), config = config, random = 1).ids())
        assertNotEquals(first, pick(library, ramp(100), config = config, random = 2).ids())
    }

    @Test
    fun `no artist repeats inside the gap when the batch can avoid it`() {
        val library = (0L until 10L).flatMap { a -> (1L..3L).map { by(a * 3 + it, if (a == 0L) 1000 else a) } }
        val config = nearestOnly.copy(artistGap = 4, batchSize = 30)

        val artists = pick(library, ramp(30), config = config).map { it.artistId }

        assertEquals(30, artists.size)
        artists.forEachIndexed { index, who ->
            assertTrue(who !in artists.drop(index + 1).take(config.artistGap), "artist $who repeats at $index")
        }
    }
}
