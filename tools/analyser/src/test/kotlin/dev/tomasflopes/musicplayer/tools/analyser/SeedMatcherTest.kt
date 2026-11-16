package dev.tomasflopes.musicplayer.tools.analyser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeedMatcherTest {
    private val paths =
        listOf(
            "HH/Valete/Valete - Fim Da Ditadura.mp3",
            "HH/Valete/Valete - Outra.mp3",
            "HH/Dillaz/Dillaz - Copo De Balão.mp3",
            "HH/Chill/Dillaz - Copo De Balão.mp3",
        )

    @Test
    fun `one match gives its index`() {
        assertEquals(0, SeedMatcher.find(paths, "Valete/Valete - Fim"))
    }

    @Test
    fun `case and the way an accent is encoded do not matter`() {
        assertEquals(2, SeedMatcher.find(paths, "dillaz/dillaz - copo de balão"))
    }

    @Test
    fun `no match is an error that names the seed`() {
        val e = assertThrows(UsageException::class.java) { SeedMatcher.find(paths, "Nobody") }

        assertTrue("Nobody" in e.message.orEmpty())
    }

    @Test
    fun `several matches are an error that lists every one`() {
        val e = assertThrows(UsageException::class.java) { SeedMatcher.find(paths, "Valete") }

        val message = e.message.orEmpty()
        assertTrue(paths[0] in message && paths[1] in message)
        assertTrue(paths[2] !in message)
    }

    @Test
    fun `a long list of matches is cut and says how many are not shown`() {
        val many = List(30) { "a/song $it.mp3" }

        val e = assertThrows(UsageException::class.java) { SeedMatcher.find(many, "song") }

        assertTrue("10 more" in e.message.orEmpty())
    }
}
