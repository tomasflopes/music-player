package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeedKind
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.lists.song
import dev.tomasflopes.musicplayer.core.taste.shuffle.BatchOrdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** ADR 0086: a mix batch is shuffled after it is chosen; the choice itself is the engine's, unchanged. */
class MixEngineShuffleTest {
    private fun by(
        id: Long,
        artist: Long = id,
    ): Song = song(id, 0).copy(artistId = ArtistId(artist), tagArtistName = "artist $artist")

    private val library = (1L..100L).map { by(it, artist = it % 17) }
    private val space = SoundSpace((1L..100L).toList().toLongArray(), 1, (1..100).map { it.toFloat() }.toFloatArray())
    private val artistSeed = MixSeed(MixSeedKind.ARTIST, 5)
    private val seeds = listOf(MixSeed(MixSeedKind.SONG, 10), MixSeed(MixSeedKind.SONG, 60), artistSeed)
    private val config = MixConfig()

    private fun List<Song>.ids() = map { it.id.value }

    private fun pick(
        seed: Int,
        ordering: BatchOrdering? = null,
        config: MixConfig = this.config,
    ): List<Song> {
        val engine = if (ordering == null) MixEngine(config) else MixEngine(config, ordering = ordering)
        return engine.pick(seeds, library, space, setOf(SongId(11)), Random(seed), MixLearning.EMPTY)
    }

    private fun refill(
        seed: Int,
        exclude: List<Song>,
        preceding: List<ArtistId>,
        ordering: BatchOrdering? = null,
    ): List<Song> {
        val engine = if (ordering == null) MixEngine(config) else MixEngine(config, ordering = ordering)
        return engine.refill(seeds, library, space, emptySet(), Random(seed), exclude, preceding, MixLearning.EMPTY)
    }

    private fun assertGap(
        artists: List<ArtistId>,
        from: Int,
        gap: Int,
        label: String,
    ) {
        for (index in from until artists.size) {
            val before = artists.subList(maxOf(0, index - gap), index)
            assertTrue(artists[index] !in before, "$label: artist ${artists[index]} repeats at $index")
        }
    }

    @Test
    fun `a shuffled start is a permutation of the batch it would have had`() {
        (1..30).forEach { seed ->
            val plain = pick(seed, BatchOrdering.Identity)
            val shuffled = pick(seed)

            assertEquals(plain.size, shuffled.size)
            assertEquals(plain.ids().toSet(), shuffled.ids().toSet(), "seed $seed changed the selection")
        }
    }

    @Test
    fun `over 100 starts the first song is not always the same`() {
        val plainLeaders = (1..100).map { pick(it, BatchOrdering.Identity).first().id.value }.toSet()
        val shuffledLeaders = (1..100).map { pick(it).first().id.value }.toSet()

        assertTrue(plainLeaders.size <= 3, "unshuffled leaders were expected to be nearly fixed: $plainLeaders")
        assertTrue(shuffledLeaders.size > 1, "every start led with the same song: $shuffledLeaders")
    }

    @Test
    fun `a shuffled start keeps no artist twice within the gap`() {
        (1..100).forEach { seed ->
            assertGap(pick(seed).map { it.artistId }, from = 0, gap = config.artistGap, label = "seed $seed")
        }
    }

    @Test
    fun `the seed artist's songs are still in the batch, just not always first`() {
        val own = { batch: List<Song> -> batch.filter { it.artistId == ArtistId(5) }.ids().toSet() }

        (1..30).forEach { seed ->
            assertEquals(own(pick(seed, BatchOrdering.Identity)), own(pick(seed)), "seed $seed")
            assertTrue(own(pick(seed)).size >= config.openingPerArtist, "seed $seed lost the seed artist")
        }
        assertTrue((1..100).any { pick(it).first().artistId != ArtistId(5) })
    }

    @Test
    fun `shuffleBatch false gives exactly the old order`() {
        (1..10).forEach { seed ->
            assertEquals(
                pick(seed, BatchOrdering.Identity).ids(),
                pick(seed, config = config.copy(shuffleBatch = false)).ids(),
            )
        }
    }

    @Test
    fun `a seeded random gives a reproducible order and another seed another one`() {
        assertEquals(pick(5).ids(), pick(5).ids())
        assertNotEquals(pick(5).ids(), pick(6).ids())
    }

    @Test
    fun `a shuffled refill is a permutation of the batch it would have had`() {
        val queued = library.filter { it.id.value <= 10 }
        val preceding = listOf(ArtistId(1))

        (1..30).forEach { seed ->
            val plain = refill(seed, queued, preceding, BatchOrdering.Identity)
            val shuffled = refill(seed, queued, preceding)

            assertEquals(plain.ids().toSet(), shuffled.ids().toSet(), "seed $seed changed the selection")
            assertEquals(plain.size, shuffled.size)
        }
    }

    @Test
    fun `a shuffled refill keeps the gap against the queue tail and leaves the queue alone`() {
        val queued = library.filter { it.id.value <= 10 }
        val before = queued.ids()
        val tail = queued.takeLast(config.artistGap).map { it.artistId }

        (1..100).forEach { seed ->
            val batch = refill(seed, queued, tail)

            assertTrue(batch.ids().none { it in before }, "seed $seed queued a song twice")
            assertGap(tail + batch.map { it.artistId }, from = tail.size, gap = config.artistGap, label = "seed $seed")
        }
        assertEquals(before, queued.ids())
    }

    @Test
    fun `over 100 refills the first song is not always the same`() {
        val queued = library.filter { it.id.value <= 10 }

        val leaders = (1..100).map { refill(it, queued, listOf(ArtistId(1))).first().id.value }.toSet()

        assertTrue(leaders.size > 1, "every refill led with the same song: $leaders")
    }

    @Test
    fun `refill with shuffleBatch false keeps the old order`() {
        val queued = library.filter { it.id.value <= 10 }
        val off = MixEngine(config.copy(shuffleBatch = false))

        (1..10).forEach { seed ->
            val preceding = listOf(ArtistId(1))
            val unshuffled =
                off.refill(seeds, library, space, emptySet(), Random(seed), queued, preceding, MixLearning.EMPTY)

            assertEquals(refill(seed, queued, preceding, BatchOrdering.Identity).ids(), unshuffled.ids())
        }
    }
}
