package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.taste.config.AlbumLinkRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlbumLinkFilterTest : SuggestionFilterContract() {
    override fun filter() = AlbumLinkFilter(AlbumLinkRule(weight = 2.0))

    override fun context() = contextOf(listOf(suggestSong(1, artist = 10, album = 5)))

    override fun related(): Song = suggestSong(2, artist = 11, album = 5)

    @Test
    fun `the same album scores the album weight`() {
        assertEquals(2.0, filter().points(related(), context()), 1e-9)
    }

    @Test
    fun `another album scores nothing`() {
        assertEquals(0.0, filter().points(suggestSong(2, album = 6), context()), 1e-9)
    }

    @Test
    fun `a song with no album scores nothing`() {
        assertEquals(0.0, filter().points(suggestSong(2, album = null), context()), 1e-9)
    }

    @Test
    fun `more playlist songs on the album score more`() {
        val crowded = contextOf((1L..3L).map { suggestSong(it, artist = 10, album = 5) })

        assertTrue(filter().points(related(), crowded) > filter().points(related(), context()))
    }
}
