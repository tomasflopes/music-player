package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.config.IsolationGuardConfig
import dev.tomasflopes.musicplayer.core.model.sound.IsolationGuard
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
        guard: IsolationGuard = IsolationGuard(),
    ): List<Song> =
        RadioPolicy(config, guard).pick(seed, library, space, recent.mapTo(HashSet()) { SongId(it) }, Random(random))

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
    fun `the default share is about 12 percent and the rest is nearest`() {
        val library = (1L..100L).map { by(it) }

        val followers = pick(library, ramp(100), config = RadioConfig(explorationBand = 40)).drop(1).ids()

        // 24 songs after the seed: 3 exploration (12 %) and 21 nearest (songs 2..22).
        assertEquals((2L..22L).toSet(), followers.filter { it <= 22 }.toSet())
        assertEquals(3, followers.count { it > 22 })
    }

    @Test
    fun `exploration is drawn only from the band of nearest songs`() {
        val library = (1L..200L).map { by(it) }
        val config = RadioConfig(explorationShare = 0.5, nearestShare = 0.5, explorationBand = 40)

        (1..20).forEach { random ->
            val followers = pick(library, ramp(200), config = config, random = random).drop(1).ids()

            // Band = the 40 nearest candidates (songs 2..41); nothing from the rest of the library.
            assertTrue(followers.all { it <= 41 }, "seed $random drew outside the band: $followers")
            assertEquals(24, followers.size)
        }
    }

    @Test
    fun `exploration picks are never songs already chosen as nearest`() {
        val library = (1L..60L).map { by(it) }
        val config = RadioConfig(explorationShare = 0.5, nearestShare = 0.5, explorationBand = 30)

        val batch = pick(library, ramp(60), config = config).ids()

        assertEquals(batch.size, batch.toSet().size)
        assertEquals(25, batch.size)
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
        val config = RadioConfig(explorationBand = 100)

        val first = pick(library, space, config = config, random = 1).ids()

        assertEquals(first, pick(library, space, config = config, random = 1).ids())
        assertNotEquals(first, pick(library, space, config = config, random = 2).ids())
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
    fun `the config rejects a band smaller than the batch`() {
        assertThrows(IllegalArgumentException::class.java) { RadioConfig(batchSize = 25, explorationBand = 24) }
    }

    @Test
    fun `the config rejects shares that do not add up`() {
        assertThrows(IllegalArgumentException::class.java) { RadioConfig(nearestShare = 0.5, explorationShare = 0.3) }
    }

    private fun refill(
        library: List<Song>,
        space: SoundSpace,
        seed: Song = library.first(),
        exclude: List<Song> = listOf(seed),
        preceding: List<Long> = emptyList(),
        config: RadioConfig = RadioConfig(batchSize = 5, nearestShare = 1.0, explorationShare = 0.0, artistGap = 0),
        random: Int = 1,
    ): List<Song> =
        RadioPolicy(config).refill(
            seed,
            library,
            space,
            emptySet(),
            Random(random),
            exclude,
            preceding.map { ArtistId(it) },
        )

    @Test
    fun `a refill has the configured size and leaves out the seed`() {
        val library = (1L..30L).map { by(it) }

        val batch = refill(library, ramp(30))

        assertEquals(listOf(2L, 3L, 4L, 5L, 6L), batch.ids())
    }

    @Test
    fun `a refill never picks a song that is already queued`() {
        val library = (1L..30L).map { by(it) }
        val queued = library.take(8)

        val batch = refill(library, ramp(30), exclude = queued)

        assertEquals(listOf(9L, 10L, 11L, 12L, 13L), batch.ids())
    }

    @Test
    fun `a refill leaves out a copy of a queued recording`() {
        val library = (1L..30L).map { by(it) }.toMutableList()
        // Song 3 is the same recording as song 2 under another file name.
        library[2] =
            library[2].copy(title = library[1].title, tagArtistName = library[1].tagArtistName, fileName = "x.mp3")

        val batch = refill(library, ramp(30), exclude = listOf(library[0], library[1]))

        assertTrue(3L !in batch.ids())
    }

    @Test
    fun `a refill keeps the artist gap against the songs queued before it`() {
        val library = (1L..30L).map { by(it, artist = if (it == 2L) 99 else it) }
        val gap = RadioConfig(batchSize = 5, nearestShare = 1.0, explorationShare = 0.0, artistGap = 4)

        val batch = refill(library, ramp(30), preceding = listOf(7L, 8L, 99L), config = gap)

        assertTrue(batch.first().artistId.value != 99L)
        assertEquals(5, batch.size)
    }

    @Test
    fun `a refill from a seed without a point in the space is empty`() {
        val library = (1L..10L).map { by(it) }

        assertTrue(refill(library, ramp(5), seed = library[8]).isEmpty())
    }

    // ADR 0087: song 1 sits alone at 0, songs 2..61 form a dense group from 10, and songs 100 and 101 (the seed's
    // artist) sit far away at 40 and 41. The guard uses rank 2 here so the small space is enough.
    private val outlierLibrary =
        listOf(by(1)) + (2L..61L).map { by(it) } + listOf(by(100, artist = 1), by(101, artist = 1))
    private val outlierSpace =
        line(
            1L to 0f,
            *(2L..61L).map { it to 10f + (it - 2) * 0.1f }.toTypedArray(),
            100L to 40f,
            101L to 41f,
        )

    @Test
    fun `an isolated seed's nearest part takes its own artist's songs ahead of farther sound neighbours`() {
        val guard = IsolationGuard(IsolationGuardConfig(rank = 2))

        val batch = pick(outlierLibrary, outlierSpace, config = nearestOnly, guard = guard)

        // 24 nearest slots, 7 of them for the seed's artist; the cap (3 per artist, the seed counts) leaves 2 own
        // songs, so 22 sound songs (2..23) and then the two own songs, all in order of distance.
        assertEquals(listOf(1L) + (2L..23L).toList() + listOf(100L, 101L), batch.ids())
    }

    @Test
    fun `a seed that is not isolated gets the plain nearest part`() {
        val never = IsolationGuard(IsolationGuardConfig(rank = 2, thresholdPercentile = 100.0))

        val batch = pick(outlierLibrary, outlierSpace, config = nearestOnly, guard = never)

        assertEquals((1L..25L).toList(), batch.ids())
    }
}
