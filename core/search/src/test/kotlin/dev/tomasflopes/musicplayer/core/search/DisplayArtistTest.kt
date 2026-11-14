package dev.tomasflopes.musicplayer.core.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisplayArtistTest {
    @Test
    fun `parses artist before the first spaced hyphen`() {
        assertEquals("Rui Veloso", DisplayArtist.fromFileName("Rui Veloso - Anel de Rubi.mp3"))
        assertEquals("Rui Veloso", DisplayArtist.fromFileName("Rui Veloso - Anel - Live.mp3"))
        assertEquals("Rui Veloso", DisplayArtist.fromFileName("Rui Veloso – Anel de Rubi.mp3"))
    }

    @Test
    fun `no spaced separator means no artist`() {
        assertNull(DisplayArtist.fromFileName("Anel de Rubi.mp3"))
        assertNull(DisplayArtist.fromFileName("Jay-Z.mp3"))
        assertNull(DisplayArtist.fromFileName("Rui-Veloso-Anel.mp3"))
        assertNull(DisplayArtist.fromFileName(" - Anel.mp3"))
    }

    @Test
    fun `track number prefixes are dropped`() {
        assertEquals("Rui Veloso", DisplayArtist.fromFileName("01 - Rui Veloso - Anel.mp3"))
        assertEquals("Rui Veloso", DisplayArtist.fromFileName("01. Rui Veloso - Anel.mp3"))
        assertEquals("Rui Veloso", DisplayArtist.fromFileName("01 Rui Veloso - Anel.mp3"))
        assertEquals("Rui Veloso", DisplayArtist.fromFileName("1) Rui Veloso - Anel.mp3"))
    }

    @Test
    fun `names that start with digits are kept`() {
        assertEquals("21 Savage", DisplayArtist.fromFileName("21 Savage - a lot.mp3"))
        assertEquals("50 Cent", DisplayArtist.fromFileName("50 Cent - In da Club.mp3"))
        assertEquals("10cc", DisplayArtist.fromFileName("10cc - Dreadlock Holiday.mp3"))
        assertNull(DisplayArtist.fromFileName("01 - Anel de Rubi.mp3"))
    }

    @Test
    fun `feat text is not part of the artist`() {
        assertEquals("Rui Veloso", DisplayArtist.fromFileName("Rui Veloso feat. Ana - Anel.mp3"))
        assertEquals("Rui Veloso", DisplayArtist.fromFileName("Rui Veloso ft Ana - Anel.mp3"))
    }

    @Test
    fun `comparison ignores case, accents, punctuation and special characters`() {
        assertTrue(DisplayArtist.matches("Beyoncé", "BEYONCE"))
        assertTrue(DisplayArtist.matches("JAY-Z", "Jay Z"))
        assertTrue(DisplayArtist.matches("A\$AP Rocky", "ASAP Rocky"))
        assertTrue(DisplayArtist.matches("P!nk", "pnk"))
        assertFalse(DisplayArtist.matches("Guitarra", "Rui Veloso"))
    }

    @Test
    fun `one name of a credit matches`() {
        assertTrue(DisplayArtist.matches("Rui Veloso", "Rui Veloso & Carlos Tê"))
        assertTrue(DisplayArtist.matches("Carlos Tê", "Rui Veloso, Carlos Tê"))
        assertFalse(DisplayArtist.matches("Queen", "Queen Latifah"))
    }

    @Test
    fun `a playlist like tag shows the file name artist`() {
        assertEquals("Rui Veloso", DisplayArtist.of("Guitarra", "Rui Veloso - Anel de Rubi.mp3"))
    }

    @Test
    fun `a matching tag keeps the tag spelling`() {
        assertEquals("Beyoncé", DisplayArtist.of("Beyoncé", "BEYONCE - Halo.mp3"))
        assertEquals("Guitarra", DisplayArtist.of("Guitarra", "Guitarra - Anel.mp3"))
    }

    @Test
    fun `no artist in the file name keeps the tag`() {
        assertEquals("Guitarra", DisplayArtist.of("Guitarra", "Anel de Rubi.mp3"))
    }
}
