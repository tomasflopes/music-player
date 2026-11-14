package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongArtists
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.taste.config.ArtistLinkRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtistLinkFilterTest : SuggestionFilterContract() {
    private val rule = ArtistLinkRule(main = 3.0, featured = 2.0)

    override fun filter() = ArtistLinkFilter(rule)

    override fun context() = contextOf(listOf(suggestSong(1, artist = 10)))

    override fun related(): Song = suggestSong(2, artist = 10)

    @Test
    fun `the same main artist scores the main weight`() {
        assertEquals(3.0, filter().points(suggestSong(2, artist = 10), context()), 1e-9)
    }

    @Test
    fun `an unrelated artist scores nothing`() {
        assertEquals(0.0, filter().points(suggestSong(2, artist = 11), context()), 1e-9)
    }

    @Test
    fun `a candidate that features a playlist artist scores the featured weight`() {
        // The candidate's main artist is 20 and its title features artist 10, who is in the playlist.
        val candidate = suggestSong(2, artist = 20)
        val context =
            SuggestionContext(
                members = listOf(suggestSong(1, artist = 10)),
                dismissed = emptyList(),
                artists =
                    mapOf(
                        SongId(1) to SongArtists(ArtistId(10)),
                        SongId(2) to SongArtists(ArtistId(20), listOf(ArtistId(10))),
                    ),
            )

        assertEquals(2.0, filter().points(candidate, context), 1e-9)
    }

    @Test
    fun `a playlist song that features the candidate's artist scores the featured weight`() {
        val context = contextOf(listOf(suggestSong(1, artist = 10)), featured = mapOf(SongId(1) to listOf(20L)))

        assertEquals(2.0, filter().points(suggestSong(2, artist = 20), context), 1e-9)
    }

    @Test
    fun `more playlist songs behind the link score more, with diminishing returns`() {
        val one = contextOf(listOf(suggestSong(1, artist = 10)))
        val three = contextOf((1L..3L).map { suggestSong(it, artist = 10) })
        val candidate = suggestSong(9, artist = 10)

        val few = filter().points(candidate, one)
        val many = filter().points(candidate, three)

        assertTrue(many > few)
        assertTrue(many < few * 3)
    }
}
