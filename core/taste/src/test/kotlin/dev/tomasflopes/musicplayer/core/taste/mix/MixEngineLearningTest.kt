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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** ADR 0085: what a mix learned from its own rows steers the next batch; the engine stays pure. */
class MixEngineLearningTest {
    private fun by(
        id: Long,
        artist: Long = id,
    ): Song = song(id, 0).copy(artistId = ArtistId(artist), tagArtistName = "artist $artist")

    private fun songSeed(id: Long) = MixSeed(MixSeedKind.SONG, id)

    private fun learning(
        liked: List<Long> = emptyList(),
        banned: Set<Long> = emptySet(),
        playedInMix: Set<Long> = emptySet(),
        informativeRows: Int = 0,
    ) = MixLearning(
        liked = liked.map { SongId(it) },
        banned = banned.mapTo(HashSet()) { SongId(it) },
        playedInMix = playedInMix.mapTo(HashSet()) { SongId(it) },
        informativeRows = informativeRows,
    )

    private fun List<Song>.ids() = map { it.id.value }

    // Two arms in a plane. The seed S (id 100) sits at the origin; x_k (id k) at (10k, 0) and y_k (id 20 + k) at
    // (0, 10k), k = 1..8. With density rank 3, S, x1 and every arm song have a reach of 10 (y8 has 20), so a song's key
    // against S alone is k for both arms, and a liked x1 pulls the x arm one step closer.
    private val seedSong = 100L
    private val plane: List<Song> = (1L..8L).map { by(it) } + (21L..28L).map { by(it) } + by(seedSong)
    private val planeSpace =
        run {
            val ids = (1L..8L) + (21L..28L) + seedSong
            val values =
                ids.flatMap { id ->
                    when {
                        id == seedSong -> listOf(0f, 0f)
                        id <= 8L -> listOf(10f * id, 0f)
                        else -> listOf(0f, 10f * (id - 20))
                    }
                }
            SoundSpace(ids.toLongArray(), 2, values.toFloatArray())
        }
    private val tight =
        MixConfig(
            batchSize = 5,
            explorationShare = 0.0,
            explorationFloor = 0.0,
            explorationBand = 5,
            densityRank = 3,
            artistGap = 0,
            openingPerArtist = 0,
            refillLowWater = 1,
        )

    private fun inPlane(
        learning: MixLearning,
        seeds: List<MixSeed> = listOf(songSeed(seedSong)),
        config: MixConfig = tight,
    ) = MixEngine(config).pick(seeds, plane, planeSpace, emptySet(), Random(1), learning).ids()

    @Test
    fun `a liked song close to the seeds becomes an anchor and pulls its neighbourhood into the batch`() {
        val plain = inPlane(MixLearning.EMPTY)
        val learned = inPlane(learning(liked = listOf(1)))

        assertEquals(setOf(100L, 1L, 21L, 2L, 22L), plain.toSet())
        assertEquals(setOf(100L, 1L, 2L, 21L, 3L), learned.toSet())
    }

    @Test
    fun `a liked song beyond the gate is not an anchor, so the batch is the one without learning`() {
        // x5 is 5 reaches from the seed, far past the gate of 1.5.
        assertEquals(inPlane(MixLearning.EMPTY), inPlane(learning(liked = listOf(5))))
    }

    @Test
    fun `the gate applies before the cap, so a far liked song does not use up a slot`() {
        val oneSlot = tight.copy(likedMax = 1)

        assertEquals(
            inPlane(learning(liked = listOf(1)), config = oneSlot),
            inPlane(learning(liked = listOf(5, 1)), config = oneSlot),
        )
    }

    @Test
    fun `a liked song that is not in the library or the space is ignored`() {
        assertEquals(inPlane(MixLearning.EMPTY), inPlane(learning(liked = listOf(9_999))))
    }

    @Test
    fun `a seed edit drops stale anchors, because the gate is measured against the current seeds only`() {
        val liked = learning(liked = listOf(1))
        val newSeeds = listOf(songSeed(28))

        assertNotEquals(inPlane(MixLearning.EMPTY), inPlane(liked))
        assertEquals(inPlane(MixLearning.EMPTY, newSeeds), inPlane(liked, newSeeds))
    }

    @Test
    fun `a liked song played in the cool-down does not re-enter the batch`() {
        val two = tight.copy(batchSize = 2, explorationBand = 2)

        assertTrue(21L in inPlane(learning(liked = listOf(21)), config = two))
        assertFalse(21L in inPlane(learning(liked = listOf(21), playedInMix = setOf(21)), config = two))
    }

    @Test
    fun `banned songs are not candidates, the seed's own song included`() {
        val banned = learning(banned = setOf(1, seedSong))

        val batch = inPlane(banned)

        assertEquals(5, batch.size)
        assertFalse(1L in batch)
        assertFalse(seedSong in batch)
    }

    @Test
    fun `a banned song is also left out of the opening songs of an artist seed`() {
        val library = (1L..30L).map { by(it, artist = if (it == 3L || it == 4L) 99 else it) }
        val space = SoundSpace((1L..30L).toList().toLongArray(), 1, (1..30).map { it.toFloat() }.toFloatArray())
        val config = tight.copy(batchSize = 10, explorationBand = 10, openingPerArtist = 2)
        val seed = listOf(MixSeed(MixSeedKind.ARTIST, 99))

        fun batch(learning: MixLearning) =
            MixEngine(config).pick(seed, library, space, emptySet(), Random(1), learning).ids()

        assertTrue(batch(MixLearning.EMPTY).containsAll(listOf(3L, 4L)))
        val without = batch(learning(banned = setOf(3)))
        assertFalse(3L in without)
        assertTrue(4L in without)
    }

    @Test
    fun `a refill leaves banned songs out too`() {
        val batch =
            MixEngine(tight).refill(
                listOf(songSeed(seedSong)),
                plane,
                planeSpace,
                emptySet(),
                Random(1),
                emptyList(),
                emptyList(),
                learning(banned = setOf(1)),
            )

        assertFalse(1L in batch.ids())
    }

    @Test
    fun `exploration falls with the informative rows, so the nearest part grows from 17 to 20 to 22 songs`() {
        val library = (1L..100L).map { by(it) }
        val space = SoundSpace((1L..100L).toList().toLongArray(), 1, (1..100).map { it.toFloat() }.toFloatArray())
        val config = MixConfig(explorationBand = 60, artistGap = 0, openingPerArtist = 0)

        fun batches(rows: Int) =
            (1..30).map {
                MixEngine(config)
                    .pick(listOf(songSeed(1)), library, space, emptySet(), Random(it), learning(informativeRows = rows))
                    .ids()
                    .toSet()
            }

        assertTrue(batches(0).all { it.containsAll((1L..17L).toList()) })
        assertTrue(batches(0).any { !it.containsAll((1L..22L).toList()) })
        assertTrue(batches(100).all { it.containsAll((1L..20L).toList()) })
        assertTrue(batches(200).all { it.containsAll((1L..22L).toList()) })
        assertTrue(batches(2_000).all { it.containsAll((1L..22L).toList()) })
    }

    // The slice 2 batches of this fixture, captured from the engine before learning existed (golden): an empty
    // MixLearning must reproduce them exactly.
    private val goldenLibrary = (1L..100L).map { by(it, artist = it % 17) }
    private val goldenSpace =
        SoundSpace((1L..100L).toList().toLongArray(), 1, (1..100).map { it.toFloat() }.toFloatArray())
    private val goldenSeeds = listOf(songSeed(10), songSeed(60), MixSeed(MixSeedKind.ARTIST, 5))

    @Test
    fun `an empty learning gives exactly the slice 2 batch`() {
        val pick =
            MixEngine(MixConfig()).pick(
                goldenSeeds,
                goldenLibrary,
                goldenSpace,
                setOf(SongId(11)),
                Random(1),
                MixLearning.EMPTY,
            )

        assertEquals(
            listOf(39L, 10, 60, 47, 86, 56, 48, 9, 16, 59, 61, 12, 40, 49, 8, 95, 46, 7, 13, 62, 6, 97, 75, 15, 58),
            pick.ids(),
        )
    }

    @Test
    fun `an empty learning gives exactly the slice 2 refill`() {
        val refill =
            MixEngine(MixConfig()).refill(
                goldenSeeds,
                goldenLibrary,
                goldenSpace,
                emptySet(),
                Random(1),
                goldenLibrary.filter { it.id.value <= 10 },
                listOf(ArtistId(1)),
                MixLearning.EMPTY,
            )

        assertEquals(
            listOf(60L, 47, 48, 11, 86, 59, 16, 61, 12, 49, 40, 13, 95, 46, 58, 62, 14, 15, 50, 75, 45, 72, 57, 63, 55),
            refill.ids(),
        )
    }
}
