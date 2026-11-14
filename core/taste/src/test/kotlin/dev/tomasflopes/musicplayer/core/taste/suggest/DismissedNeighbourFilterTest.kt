package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.taste.config.DismissedRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DismissedNeighbourFilterTest : SuggestionFilterContract() {
    private val rule = DismissedRule(artist = 1.5, album = 1.0, maxCount = 2)

    override fun filter() = DismissedNeighbourFilter(rule)

    override fun context() =
        contextOf(
            members = listOf(suggestSong(1, artist = 10)),
            dismissed = listOf(suggestSong(5, artist = 20, album = 7)),
        )

    override fun related(): Song = suggestSong(2, artist = 20, album = 7)

    @Test
    fun `it only ranks and never qualifies a song`() {
        assertFalse(filter().isRelevanceCheck)
    }

    @Test
    fun `nothing dismissed costs nothing`() {
        assertTrue(filter().points(related(), contextOf(listOf(suggestSong(1)))) == 0.0)
    }

    @Test
    fun `the same artist and the same album both cost points`() {
        assertEquals(-2.5, filter().points(related(), context()), 1e-9)
    }

    @Test
    fun `only the artist matches when the album differs`() {
        assertEquals(-1.5, filter().points(suggestSong(2, artist = 20, album = 8), context()), 1e-9)
    }

    @Test
    fun `an unrelated song costs nothing`() {
        assertTrue(filter().points(suggestSong(2, artist = 30, album = 9), context()) == 0.0)
    }

    @Test
    fun `the cost stops growing after maxCount dismissed songs`() {
        val many = contextOf(emptyList(), dismissed = (1L..6L).map { suggestSong(it, artist = 20) })
        val two = contextOf(emptyList(), dismissed = (1L..2L).map { suggestSong(it, artist = 20) })
        val candidate = suggestSong(9, artist = 20)

        assertEquals(filter().points(candidate, two), filter().points(candidate, many), 1e-9)
    }
}
