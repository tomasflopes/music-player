package dev.tomasflopes.musicplayer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SectionIndexerTest {
    @Test
    fun sections() {
        val expected = listOf(Section('#', 0), Section('A', 1), Section('B', 2), Section('Z', 4))

        assertEquals(expected, SectionIndexer.sections(listOf("2pac", "album", "beta", "beta 2", "zed")))
    }

    @Test
    fun `accented letters go under their base letter`() {
        assertEquals(listOf(Section('E', 0)), SectionIndexer.sections(listOf("Élan", "edge")))
    }

    @Test
    fun `symbols and empty keys go under hash`() {
        assertEquals(listOf(Section('#', 0)), SectionIndexer.sections(listOf("\$uicide", "", "(intro)")))
    }

    @Test
    fun `a label appears once even if it comes back`() {
        assertEquals(listOf(Section('#', 0), Section('A', 1)), SectionIndexer.sections(listOf("1", "a", "2")))
    }

    @Test
    fun `no keys, no sections`() {
        assertEquals(emptyList<Section>(), SectionIndexer.sections(emptyList()))
    }
}
