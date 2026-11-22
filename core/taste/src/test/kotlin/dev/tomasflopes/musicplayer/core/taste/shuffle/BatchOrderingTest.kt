package dev.tomasflopes.musicplayer.core.taste.shuffle

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.taste.config.SmartShuffleConfig
import dev.tomasflopes.musicplayer.core.taste.lists.song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** ADR 0086: the batch step that puts a finished mix or artist radio batch in play order. */
class BatchOrderingTest {
    private fun by(
        id: Long,
        artist: Long,
    ): Song = song(id, 0).copy(artistId = ArtistId(artist), tagArtistName = "artist $artist")

    /** 25 songs, five artists of five songs each, grouped by artist the way a nearest-first batch can be. */
    private val batch = (1L..25L).map { by(it, artist = (it - 1) / 5) }

    private fun List<Song>.ids() = map { it.id.value }

    @Test
    fun `identity returns the batch as it is`() {
        assertEquals(batch, BatchOrdering.Identity.order(batch, emptyList(), Random(1)))
    }

    @Test
    fun `smart ordering is a permutation of the batch`() {
        val ordered = BatchOrdering.smart().order(batch, emptyList(), Random(1))

        assertEquals(batch.size, ordered.size)
        assertEquals(batch.ids().toSet(), ordered.ids().toSet())
        assertNotEquals(batch.ids(), ordered.ids())
    }

    @Test
    fun `smart ordering keeps every artist at least the gap apart`() {
        val gap = SmartShuffleConfig().artistGap

        (1..50).forEach { seed ->
            val artists = BatchOrdering.smart().order(batch, emptyList(), Random(seed)).map { it.artistId }

            artists.forEachIndexed { index, who ->
                assertTrue(who !in artists.drop(index + 1).take(gap), "seed $seed: $who repeats at $index")
            }
        }
    }

    @Test
    fun `smart ordering keeps the gap against the songs that precede the batch`() {
        val gap = SmartShuffleConfig().artistGap
        val preceding = listOf(ArtistId(0), ArtistId(1), ArtistId(2), ArtistId(3))

        (1..50).forEach { seed ->
            val artists = preceding + BatchOrdering.smart().order(batch, preceding, Random(seed)).map { it.artistId }

            artists.forEachIndexed { index, who ->
                if (index >= preceding.size) {
                    assertTrue(who !in artists.subList(maxOf(0, index - gap), index), "seed $seed: $who at $index")
                }
            }
        }
    }

    @Test
    fun `the same random gives the same order and another random another one`() {
        val ordering = BatchOrdering.smart()

        val first = ordering.order(batch, emptyList(), Random(7)).ids()

        assertEquals(first, ordering.order(batch, emptyList(), Random(7)).ids())
        assertNotEquals(first, ordering.order(batch, emptyList(), Random(8)).ids())
    }

    @Test
    fun `the batch given is not modified`() {
        val before = batch.ids()

        BatchOrdering.smart().order(batch, emptyList(), Random(1))

        assertEquals(before, batch.ids())
    }
}
