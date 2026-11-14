package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every [SuggestionFilter] must pass these. A new filter's test class extends this one (Guidelines 2.4). */
abstract class SuggestionFilterContract {
    abstract fun filter(): SuggestionFilter

    /** A playlist with songs that [related] has something in common with. */
    internal abstract fun context(): SuggestionContext

    /** A song the filter has a reason to look at in [context]. */
    internal abstract fun related(): Song

    private val empty = SuggestionContext(emptyList(), emptyList())

    @Test
    fun `id is non-blank and stable`() {
        assertTrue(filter().id.isNotBlank())
        assertEquals(filter().id, filter().id)
    }

    @Test
    fun `points are finite`() {
        assertTrue(filter().points(related(), context()).isFinite())
        assertTrue(filter().points(suggestSong(9_999), context()).isFinite())
    }

    @Test
    fun `the same input gives the same points`() {
        assertEquals(filter().points(related(), context()), filter().points(related(), context()))
    }

    @Test
    fun `an empty playlist gives a relevance check nothing to go on`() {
        val filter = filter()

        assertTrue(!filter.isRelevanceCheck || filter.points(related(), empty) == 0.0)
    }

    @Test
    fun `a relevance check never gives negative points`() {
        val filter = filter()

        assertTrue(!filter.isRelevanceCheck || filter.points(related(), context()) >= 0.0)
        assertTrue(!filter.isRelevanceCheck || filter.points(suggestSong(9_999), context()) >= 0.0)
    }

    @Test
    fun `a relevance check finds a reason for a related song`() {
        val filter = filter()

        assertTrue(!filter.isRelevanceCheck || filter.points(related(), context()) > 0.0)
    }
}
