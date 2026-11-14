package dev.tomasflopes.musicplayer.data.artistimage

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextNormalizerNameMatcherTest {
    private val matcher = TextNormalizerNameMatcher()

    private fun same(
        tag: String,
        deezer: String,
    ) = matcher.matches(tag, deezer)

    @Test
    fun `case, accents and punctuation do not matter`() {
        assertTrue(same("beyonce", "Beyoncé"))
        assertTrue(same("AC/DC", "ACDC"))
        assertTrue(same("Jay-Z", "JAY Z"))
        assertTrue(same("a\$ap rocky", "A\$AP Rocky"))
        assertTrue(same("asap rocky", "A\$AP Rocky"))
    }

    @Test
    fun `a short name does not take a longer artist's photo`() {
        assertFalse(same("Ed", "Ed Sheeran"))
        assertFalse(same("Queen", "Queens of the Stone Age"))
        assertFalse(same("Queen", "Queen Latifah"))
    }

    @Test
    fun `different artists with similar names do not match`() {
        assertFalse(same("Rui Veloso", "Rui Velozo"))
        assertFalse(same("The Doors", "Doors"))
    }

    @Test
    fun `a name with no letters matches nothing`() {
        assertFalse(same("???", "!!!"))
        assertFalse(same("", ""))
    }
}
