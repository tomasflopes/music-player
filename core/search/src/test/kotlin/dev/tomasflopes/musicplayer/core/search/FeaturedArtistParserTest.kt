package dev.tomasflopes.musicplayer.core.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FeaturedArtistParserTest {
    private val artists = listOf("Ed Sheeran", "Kendrick Lamar", "J. Cole", "21 Savage")

    @Test
    fun no_marker_returns_empty() {
        assertEquals(emptyList<String>(), FeaturedArtistParser.parse("HUMBLE.", artists))
        assertEquals(emptyList<String>(), FeaturedArtistParser.parse("Song feat", artists))
    }

    @Test
    fun matches_multi_word_longest_first() {
        assertEquals(
            listOf("Ed", "Kendrick Lamar"),
            FeaturedArtistParser.parse("Song feat. Ed Kendrick Lamar", artists),
        )
    }

    @Test
    fun accepts_feat_and_ft_case_insensitive() {
        assertEquals(listOf("J. Cole"), FeaturedArtistParser.parse("Song feat J. Cole", artists))
        assertEquals(listOf("21 Savage"), FeaturedArtistParser.parse("Song FT. 21 Savage", artists))
        assertEquals(listOf("Ed Sheeran"), FeaturedArtistParser.parse("Song ft Ed Sheeran", artists))
    }

    @Test
    fun keeps_unmatched_as_raw_text() {
        assertEquals(listOf("Mystery", "Artist"), FeaturedArtistParser.parse("Song feat. Mystery Artist", artists))
    }

    @Test
    fun marker_position() {
        assertEquals(5, FeaturedArtistParser.markerIndex("Song feat X"))
        assertEquals(null, FeaturedArtistParser.markerIndex("Song feat"))
    }
}
