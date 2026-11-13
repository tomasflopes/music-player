package dev.tomasflopes.musicplayer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NaturalOrderTest {
    private fun sorted(vararg names: String) = names.sortedWith(NaturalOrder)

    @Test
    fun `digit runs compare as numbers`() {
        assertEquals(listOf("1 - x", "2 - y", "10 - z"), sorted("10 - z", "2 - y", "1 - x"))
    }

    @Test
    fun `case is ignored`() {
        assertEquals(listOf("apple", "Banana", "cherry"), sorted("cherry", "Banana", "apple"))
    }

    @Test
    fun `numbers inside names`() {
        assertEquals(listOf("track 2 b", "track 10 a"), sorted("track 10 a", "track 2 b"))
    }

    @Test
    fun `numbers longer than a Long still compare`() {
        assertEquals(listOf("9", "123456789012345678901234567890"), sorted("123456789012345678901234567890", "9"))
    }

    @Test
    fun `equal by number, fewer leading zeros first`() {
        assertEquals(listOf("1", "01", "001"), sorted("001", "1", "01"))
    }

    @Test
    fun `a prefix comes first`() {
        assertEquals(listOf("a", "a b"), sorted("a b", "a"))
    }

    @Test
    fun `only identical strings compare equal`() {
        assertTrue(NaturalOrder.compare("A", "a") != 0)
        assertEquals(0, NaturalOrder.compare("a1", "a1"))
    }
}
