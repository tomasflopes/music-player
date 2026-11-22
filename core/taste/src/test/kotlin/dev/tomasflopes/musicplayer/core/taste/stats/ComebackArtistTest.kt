package dev.tomasflopes.musicplayer.core.taste.stats

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.stats.ArtistPlays
import dev.tomasflopes.musicplayer.core.taste.config.StatsConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComebackArtistTest {
    private val comeback = ComebackArtist(StatsConfig())

    private fun plays(vararg pairs: Pair<Long, Int>) = pairs.map { (artist, n) -> ArtistPlays(ArtistId(artist), n) }

    @Test
    fun `the artist with the largest rise wins`() {
        val result = comeback.of(plays(1L to 10, 2L to 9, 3L to 4), plays(1L to 8, 2L to 2, 3L to 3))

        assertEquals(Comeback(ArtistId(2), plays = 9, previousPlays = 2), result)
        assertEquals(7, result?.rise)
    }

    @Test
    fun `an artist missing from the previous period rose from nothing`() {
        val result = comeback.of(plays(7L to 5), plays(1L to 3))

        assertEquals(Comeback(ArtistId(7), plays = 5, previousPlays = 0), result)
    }

    @Test
    fun `equal rises go to the artist with more plays now, then to the lower id`() {
        assertEquals(ArtistId(2), comeback.of(plays(1L to 6, 2L to 8), plays(1L to 2, 2L to 4))?.artistId)
        assertEquals(ArtistId(1), comeback.of(plays(2L to 6, 1L to 6), plays(2L to 2, 1L to 2))?.artistId)
    }

    @Test
    fun `an artist who fell is never the comeback`() {
        assertNull(comeback.of(plays(1L to 2), plays(1L to 9)))
    }

    @Test
    fun `a rise below the minimum is not a comeback`() {
        assertNull(comeback.of(plays(1L to 4), plays(1L to 3)))
    }

    @Test
    fun `with nothing played in the previous period there is nothing to compare`() {
        assertNull(comeback.of(plays(1L to 30), emptyList()))
    }

    @Test
    fun `an empty current period has no comeback`() = assertNull(comeback.of(emptyList(), plays(1L to 3)))
}
