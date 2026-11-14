package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.taste.config.TasteRankRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TasteScoreFilterTest : SuggestionFilterContract() {
    private val rule = TasteRankRule(song = 1.5, artist = 1.0, scale = 6.0)

    override fun filter() = TasteScoreFilter(rule)

    override fun context() =
        contextOf(
            members = listOf(suggestSong(1, artist = 10)),
            songScores = mapOf(SongId(2) to 6.0, SongId(3) to -6.0),
            artistScores = mapOf(ArtistId(11) to 6.0),
        )

    override fun related(): Song = suggestSong(2, artist = 11)

    @Test
    fun `taste only ranks and never qualifies a song`() {
        assertFalse(filter().isRelevanceCheck)
    }

    @Test
    fun `a song with no score and an artist with no score gets nothing`() {
        assertEquals(0.0, filter().points(suggestSong(9, artist = 90), context()), 1e-9)
    }

    @Test
    fun `a loved song by a loved artist gets more than a neutral one`() {
        assertTrue(filter().points(related(), context()) > filter().points(suggestSong(9, artist = 90), context()))
    }

    @Test
    fun `a skipped song gets negative points`() {
        assertTrue(filter().points(suggestSong(3, artist = 90), context()) < 0.0)
    }

    @Test
    fun `points stay within the weights however large the scores are`() {
        val huge =
            contextOf(
                members = emptyList(),
                songScores = mapOf(SongId(2) to 1e9),
                artistScores = mapOf(ArtistId(11) to 1e9),
            )

        assertEquals(rule.song + rule.artist, filter().points(related(), huge), 1e-6)
    }
}
