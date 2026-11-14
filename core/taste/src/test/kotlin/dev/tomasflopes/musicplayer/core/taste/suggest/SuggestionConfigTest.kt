package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.taste.config.AlbumLinkRule
import dev.tomasflopes.musicplayer.core.taste.config.ArtistLinkRule
import dev.tomasflopes.musicplayer.core.taste.config.DismissedRule
import dev.tomasflopes.musicplayer.core.taste.config.SuggestionConfig
import dev.tomasflopes.musicplayer.core.taste.config.TasteRankRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SuggestionConfigTest {
    @Test
    fun `the defaults are the owner's numbers`() {
        val config = SuggestionConfig()

        assertEquals(10, config.size)
        assertEquals(1, config.minSongsOnPage)
        assertEquals(2, config.minSongsInPicker)
        assertEquals(3.0, config.artist.main)
        assertEquals(2.0, config.artist.featured)
        assertEquals(2.0, config.album.weight)
        assertEquals(1.5, config.taste.song)
        assertEquals(1.0, config.taste.artist)
        assertEquals(1.5, config.dismissed.artist)
        assertEquals(3, config.dismissed.maxCount)
    }

    @Test
    fun `bad numbers are refused`() {
        assertThrows(IllegalArgumentException::class.java) { SuggestionConfig(size = 0) }
        assertThrows(IllegalArgumentException::class.java) { SuggestionConfig(minSongsOnPage = 0) }
        assertThrows(IllegalArgumentException::class.java) { SuggestionConfig(minSongsInPicker = 0) }
        assertThrows(IllegalArgumentException::class.java) { ArtistLinkRule(main = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { AlbumLinkRule(weight = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { TasteRankRule(scale = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { DismissedRule(maxCount = 0) }
    }
}
