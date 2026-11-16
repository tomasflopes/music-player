package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RadioPolicyTest {
    /** A song by [artist] (default: an artist of its own), with a title and file name no other test song shares. */
    private fun by(
        id: Long,
        artist: Long = id,
    ): Song = song(id, 0).copy(artistId = ArtistId(artist), tagArtistName = "artist $artist")

    /** One-dimensional space: each pair is a song id and its position, so the distance is the position gap. */
    private fun line(vararg points: Pair<Long, Float>) =
        SoundSpace(points.map { it.first }.toLongArray(), 1, points.map { it.second }.toFloatArray())

    /** Songs 1..[count] at position = id, so song n is n - 1 away from song 1. */
    private fun ramp(count: Int) = line(*(1..count).map { it.toLong() to it.toFloat() }.toTypedArray())

    private val nearestOnly = RadioConfig(nearestShare = 1.0, explorationShare = 0.0, artistGap = 0)

    private fun pick(
        library: List<Song>,
        space: SoundSpace,
        seed: Song = library.first(),
        recent: Set<Long> = emptySet(),
        config: RadioConfig = RadioConfig(),
        random: Int = 1,
    ): List<Song> =
        RadioPolicy(config).pick(seed, library, space, recent.mapTo(HashSet()) { SongId(it) }, Random(random))

    private fun List<Song>.ids() = map { it.id.value }

    @Test
    fun `the seed is first and the batch is as long as the config says`() {
        val library = (1L..100L).map { by(it) }

        val batch = pick(library, ramp(100))

        assertEquals(1L, batch.first().id.value)
        assertEquals(25, batch.size)
        assertEquals(25, batch.ids().toSet().size)
    }

    @Test
    fun `the nearest part is ordered by distance to the seed`() {
        val library = (1L..5L).map { by(it) }
        val space = line(1L to 0f, 2L to 5f, 3L to 1f, 4L to 3f, 5L to 2f)

        val batch = pick(library, space, config = nearestOnly)

        assertEquals(listOf(1L, 3L, 5L, 4L, 2L), batch.ids())
    }

    @Test
    fun `the seed's own point is the query, not an average of its neighbours`() {
        val library = (1L..4L).map { by(it) }
        // Song 2 sits between 1 and 3 on the far side; only the seed's own point decides the order.
        val space = line(1L to 0f, 2L to 4f, 3L to 1f, 4L to -3f)

        val batch = pick(library, space, config = nearestOnly)

        assertEquals(listOf(1L, 3L, 4L, 2L), batch.ids())
    }

    @Test
    fun `a recently played song moves down by the penalty but is kept`() {
        val library = (1L..4L).map { by(it) }
        val space = line(1L to 0f, 2L to 0.05f, 3L to 0.1f, 4L to 0.12f)

        val plain = pick(library, space, config = nearestOnly)
        val penalised = pick(library, space, recent = setOf(2), config = nearestOnly)

        assertEquals(listOf(1L, 2L, 3L, 4L), plain.ids())
        assertEquals(listOf(1L, 3L, 4L, 2L), penalised.ids())
    }

    @Test
    fun `a recent song far beyond the penalty keeps its place`() {
        val library = (1L..4L).map { by(it) }
        val space = line(1L to 0f, 2L to 0.05f, 3L to 1f, 4L to 2f)

        val batch = pick(library, space, recent = setOf(2), config = nearestOnly)

        assertEquals(listOf(1L, 2L, 3L, 4L), batch.ids())
    }

    @Test
    fun `the exploration share comes from outside the nearest songs`() {
        val library = (1L..100L).map { by(it) }

        val followers = pick(library, ramp(100)).drop(1).ids()

        // 24 songs after the seed: 17 nearest (songs 2..18) and 7 random ones from the rest of the library.
        assertEquals((2L..18L).toSet(), followers.filter { it <= 18 }.toSet())
        assertEquals(7, followers.count { it > 18 })
    }

    @Test
    fun `a config with no exploration gives only nearest songs`() {
        val library = (1L..100L).map { by(it) }

        val batch = pick(library, ramp(100), config = RadioConfig(nearestShare = 1.0, explorationShare = 0.0))

        assertEquals((1L..25L).toList(), batch.ids())
    }

    @Test
    fun `the same seed gives the same batch and another seed another one`() {
        val library = (1L..100L).map { by(it) }
        val space = ramp(100)

        val first = pick(library, space, random = 1).ids()

        assertEquals(first, pick(library, space, random = 1).ids())
        assertNotEquals(first, pick(library, space, random = 2).ids())
    }

    @Test
    fun `the batch does not depend on the order the library comes in`() {
        val library = (1L..100L).map { by(it) }
        val seed = library.first()

        val forward = pick(library, ramp(100), seed = seed).ids()
        val backward = pick(library.reversed(), ramp(100), seed = seed).ids()

        assertEquals(forward, backward)
    }

    @Test
    fun `exploration obeys the artist cap too`() {
        // Songs 2..60 are one artist, so the nearest and the exploration part both want that artist.
        val library = listOf(by(1)) + (2L..60L).map { by(it, artist = 99) } + (61L..100L).map { by(it) }

        val batch = pick(library, ramp(100))

        assertEquals(3, batch.count { it.artistId == ArtistId(99) })
    }

    @Test
    fun `no more than three songs per artist and the seed counts`() {
        val library = (1L..10L).map { by(it, artist = 1) } + (11L..40L).map { by(it) }

        val batch = pick(library, ramp(40), config = nearestOnly)

        assertEquals(3, batch.count { it.artistId == ArtistId(1) })
        assertEquals(25, batch.size)
    }

    @Test
    fun `no artist repeats inside the gap when the batch can avoid it`() {
        val library = (0L until 10L).flatMap { artist -> (1L..3L).map { by(artist * 3 + it, artist) } }
        val config = nearestOnly.copy(artistGap = 4, batchSize = 30)

        val artists = pick(library, ramp(30), config = config).map { it.artistId }

        assertEquals(30, artists.size)
        artists.forEachIndexed { index, artist ->
            val next = artists.drop(index + 1).take(config.artistGap)
            assertTrue(artist !in next, "artist $artist repeats within ${config.artistGap} songs at $index")
        }
    }

    @Test
    fun `the gap relaxes when there are too few artists and the seed stays first`() {
        val library = (1L..3L).map { by(it, artist = 1) } + (4L..6L).map { by(it, artist = 2) }

        val batch = pick(library, ramp(6), config = RadioConfig(nearestShare = 1.0, explorationShare = 0.0))

        assertEquals(listOf(1L, 4L, 2L, 5L, 3L, 6L), batch.ids())
    }

    @Test
    fun `the seed stays first whatever the spacing does`() {
        val library = listOf(by(1, 1), by(2, 1), by(3, 2), by(4, 2), by(5, 3))

        val batch = pick(library, ramp(5), seed = library[1])

        assertEquals(2L, batch.first().id.value)
    }

    @Test
    fun `a copy of the seed with the same file name is left out`() {
        val seed = by(1)
        val sameFile = by(2).copy(fileName = seed.fileName, path = "Other/${seed.fileName}")
        val library = listOf(seed, sameFile) + (3L..6L).map { by(it) }

        val batch = pick(library, ramp(6), config = nearestOnly)

        assertTrue(2L !in batch.ids())
        assertEquals(5, batch.size)
    }

    @Test
    fun `a song with the seed's duplicate key is left out`() {
        val seed = by(1, artist = 7).copy(title = "Chill")
        val sameRecording = by(2, artist = 7).copy(title = "CHILL!", tagArtistName = "Artist 7")
        val library = listOf(seed, sameRecording) + (3L..6L).map { by(it) }

        val batch = pick(library, ramp(6), config = nearestOnly)

        assertTrue(2L !in batch.ids())
    }

    @Test
    fun `two songs with the same duplicate key never share a batch`() {
        val twinA = by(2).copy(title = "Twin", tagArtistName = "same artist")
        val twinB = by(3).copy(title = "Twin", tagArtistName = "same artist")
        val library = listOf(by(1), twinA, twinB) + (4L..6L).map { by(it) }

        val batch = pick(library, ramp(6), config = nearestOnly)

        assertEquals(listOf(1L, 2L, 4L, 5L, 6L), batch.ids())
    }

    @Test
    fun `a seed with no profile gives an empty batch`() {
        val library = (1L..5L).map { by(it) }
        val space = line(2L to 1f, 3L to 2f, 4L to 3f)

        assertEquals(emptyList<Song>(), pick(library, space, seed = library.first()))
    }

    @Test
    fun `an empty space gives an empty batch`() {
        val library = (1L..5L).map { by(it) }

        assertEquals(emptyList<Song>(), pick(library, SoundSpace(LongArray(0), 1, FloatArray(0))))
    }

    @Test
    fun `songs without a profile are never candidates`() {
        val library = (1L..8L).map { by(it) }
        val space = line(1L to 0f, 2L to 1f, 3L to 2f)

        val batch = pick(library, space)

        assertEquals(listOf(1L, 2L, 3L), batch.ids().sorted())
    }

    @Test
    fun `fewer candidates than the batch gives a shorter batch and no error`() {
        val library = (1L..4L).map { by(it) }

        val batch = pick(library, ramp(4))

        assertEquals(4, batch.size)
        assertEquals(1L, batch.first().id.value)
    }

    @Test
    fun `a seed alone in the library gives a batch of the seed`() {
        val library = listOf(by(1))

        assertEquals(listOf(1L), pick(library, ramp(1)).ids())
    }

    @Test
    fun `the config rejects shares that do not add up`() {
        assertThrows(IllegalArgumentException::class.java) { RadioConfig(nearestShare = 0.5, explorationShare = 0.3) }
    }
}
