package dev.tomasflopes.musicplayer.tools.analyser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class SimilarOptionsTest {
    @Test
    fun `profiles and seed are needed and k defaults to ten`() {
        val options = SimilarOptions.parse(listOf("--profiles", "/p.jsonl", "--seed", "Valete"))

        assertEquals(Paths.get("/p.jsonl"), options.profiles)
        assertEquals("Valete", options.seed)
        assertEquals(10, options.k)
    }

    @Test
    fun `k can be set`() {
        assertEquals(3, SimilarOptions.parse(listOf("--profiles", "/p", "--seed", "x", "--k", "3")).k)
    }

    @Test
    fun `a missing option, an unknown option or a bad k is a usage error`() {
        listOf(
            listOf("--seed", "x"),
            listOf("--profiles", "/p"),
            listOf("--profiles", "/p", "--seed", "x", "--bogus", "1"),
            listOf("--profiles", "/p", "--seed", "x", "--k", "0"),
            listOf("--profiles", "/p", "--seed"),
        ).forEach { args -> assertThrows(UsageException::class.java) { SimilarOptions.parse(args) } }
    }
}
