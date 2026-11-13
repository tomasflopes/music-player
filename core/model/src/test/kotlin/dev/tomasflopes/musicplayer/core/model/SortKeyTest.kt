package dev.tomasflopes.musicplayer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SortKeyTest {
    @Test
    fun `lower case with accents removed`() = assertEquals("edge", SortKey.of("Édge"))

    @Test
    fun `a leading article is dropped`() {
        assertEquals("weeknd", SortKey.of("The Weeknd"))
        assertEquals("tribe called quest", SortKey.of("A Tribe Called Quest"))
        assertEquals("album", SortKey.of("An Album"))
    }

    @Test
    fun `an article that is the whole text stays`() = assertEquals("the", SortKey.of("The"))

    @Test
    fun `a word that only starts like an article stays`() = assertEquals("theory", SortKey.of("Theory"))

    @Test
    fun `surrounding spaces are trimmed`() = assertEquals("abba", SortKey.of("  ABBA "))

    @Test
    fun `the rail files an article-led name under its next word`() {
        val keys = listOf("Weeknd", "Zed").map { SortKey.of(it) }
        val withArticle = listOf("The Weeknd", "Zed").map { SortKey.of(it) }

        assertEquals(SectionIndexer.sections(keys), SectionIndexer.sections(withArticle))
    }
}
