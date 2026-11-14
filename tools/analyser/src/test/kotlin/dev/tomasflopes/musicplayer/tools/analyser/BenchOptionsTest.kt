package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.BenchSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class BenchOptionsTest {
    @Test
    fun `only a root is needed and the rest takes the spike defaults`() {
        val options = BenchOptions.parse(listOf("--root", "/music"))

        assertEquals(Paths.get("/music"), options.root)
        assertEquals(60, options.sample)
        assertEquals(500, options.survey)
        assertEquals(BenchSample.DEFAULT_SEED, options.seed)
    }

    @Test
    fun `sample, survey, seed and threads can be set`() {
        val args = listOf("--root", "/m", "--sample", "10", "--survey", "20", "--seed", "7", "--threads", "2")

        val options = BenchOptions.parse(args)

        assertEquals(10, options.sample)
        assertEquals(20, options.survey)
        assertEquals(7L, options.seed)
        assertEquals(2, options.threads)
    }

    @Test
    fun `a missing root, an unknown option or a bad number is a usage error`() {
        listOf(
            emptyList(),
            listOf("--root", "/m", "--bogus", "1"),
            listOf("--root", "/m", "--sample", "zero"),
            listOf("--root"),
        ).forEach { args -> assertThrows(UsageException::class.java) { BenchOptions.parse(args) } }
    }
}
