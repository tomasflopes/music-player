package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import dev.tomasflopes.musicplayer.core.taste.shuffle.BatchOrdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** ADR 0086: an artist radio batch is shuffled after it is chosen; the choice itself is the policy's, unchanged. */
class ArtistRadioShuffleTest {
    private val artist = ArtistId(1000)

    private fun by(
        id: Long,
        artist: Long = id,
    ): Song = song(id, 0).copy(artistId = ArtistId(artist), tagArtistName = "artist $artist")

    // Ten songs of the artist and 190 of 19 other artists, on a line so the distance is the id gap.
    private val library = (1L..10L).map { by(it, 1000) } + (11L..200L).map { by(it, artist = it % 19) }
    private val space = SoundSpace((1L..200L).toList().toLongArray(), 1, (1..200).map { it.toFloat() }.toFloatArray())
    private val config = RadioConfig()

    private fun List<Song>.ids() = map { it.id.value }

    private fun pick(
        seed: Int,
        ordering: BatchOrdering? = null,
        config: RadioConfig = this.config,
    ): List<Song> {
        val policy = if (ordering == null) ArtistRadioPolicy(config) else ArtistRadioPolicy(config, ordering)
        return policy.pick(artist, library, space, emptySet(), Random(seed))
    }

    @Test
    fun `a shuffled artist radio is a permutation of the batch it would have had`() {
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

        assertEquals(1, plainLeaders.size, "the unshuffled radio always opened with the artist's nearest song")
        assertTrue(shuffledLeaders.size > 1, "every start led with the same song: $shuffledLeaders")
    }

    @Test
    fun `the artist's songs are still in the batch, just not always first`() {
        (1..30).forEach { seed ->
            val own = pick(seed).filter { it.artistId == artist }

            assertEquals(config.maxPerArtist, own.size, "seed $seed")
        }
        assertTrue((1..100).any { pick(it).first().artistId != artist })
    }

    @Test
    fun `a shuffled artist radio keeps no artist twice within the gap`() {
        (1..100).forEach { seed ->
            val artists = pick(seed).map { it.artistId }

            artists.forEachIndexed { index, who ->
                assertTrue(who !in artists.drop(index + 1).take(config.artistGap), "seed $seed: $who repeats at $index")
            }
        }
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
}
