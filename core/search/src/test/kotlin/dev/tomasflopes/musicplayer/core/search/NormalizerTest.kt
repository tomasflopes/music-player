package dev.tomasflopes.musicplayer.core.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NormalizerTest {
    @Test
    fun lowercases_and_strips_accents() {
        assertEquals("ed sheeran", Normalizer.normalize("Ed Sheeran"))
        assertEquals("jose", Normalizer.normalize("José"))
        assertEquals("kendrick lamar", Normalizer.normalize("KENDRICK  LAMAR"))
    }

    @Test
    fun splits_into_words() {
        assertEquals(listOf("ed", "sheeran"), Normalizer.words("Ed Sheeran"))
        assertEquals(listOf("jose"), Normalizer.words("José"))
        assertEquals(emptyList<String>(), Normalizer.words("   "))
    }
}
