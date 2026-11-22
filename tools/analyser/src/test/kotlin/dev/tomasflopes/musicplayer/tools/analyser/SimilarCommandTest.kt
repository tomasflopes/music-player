package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.ExportHeader
import dev.tomasflopes.musicplayer.core.analysis.ExportRecord
import dev.tomasflopes.musicplayer.core.analysis.ProfileExportCodec
import dev.tomasflopes.musicplayer.core.analysis.SoundProfile
import dev.tomasflopes.musicplayer.core.model.config.IsolationGuardConfig
import dev.tomasflopes.musicplayer.core.model.sound.IsolationGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class SimilarCommandTest {
    @TempDir
    lateinit var dir: Path

    /** A song that differs from the others only in its spectral centroid. */
    private fun song(
        path: String,
        centroid: Float,
    ): ExportRecord {
        val fixed = FloatArray(SoundProfile.FIXED_SIZE)
        fixed[SoundProfile.MEAN_LOUDNESS] = 0.1f
        fixed[SoundProfile.LOUDNESS_VARIATION] = 0.02f
        fixed[SoundProfile.BASS_RATIO] = 0.3f
        fixed[SoundProfile.SPECTRAL_CENTROID] = centroid
        return ExportRecord(path, 1_000L, 180_000L, SoundProfile(fixed, FloatArray(3)))
    }

    private val library =
        listOf(
            song("HH/A/s1.mp3", 100f),
            song("HH/A/s2.mp3", 200f),
            song("HH/A/s3.mp3", 300f),
            song("HH/A/seed.mp3", 400f),
            song("HH/B/seed.mp3", 401f),
            song("HH/A/s6.mp3", 520f),
            song("HH/A/s7.mp3", 600f),
            song("HH/A/s8.mp3", 700f),
            song("HH/A/s9.mp3", 800f),
        )

    private fun options(
        seed: String,
        k: Int = 3,
    ) = SimilarOptions(dir.resolve("p.jsonl"), seed, k)

    @Test
    fun `the nearest songs come nearest first and the seed is not among them`() {
        val text = SimilarCommand.render(library, options("HH/A/seed"))

        val rows = text.lines().filter { it.contains("d=") }.drop(1)
        assertEquals(listOf("s3.mp3", "s6.mp3"), rows.take(2).map { it.substringAfterLast('/') })
        assertTrue(rows.size == 3)
    }

    @Test
    fun `a copy of the seed with the same file name in another folder is left out`() {
        val text = SimilarCommand.render(library, options("HH/A/seed", k = 8))

        assertFalse("HH/B/seed.mp3" in text.lines().drop(3).joinToString("\n"))
    }

    @Test
    fun `a seed that matches several songs or none is a usage error`() {
        assertThrows(UsageException::class.java) { SimilarCommand.render(library, options("seed")) }
        assertThrows(UsageException::class.java) { SimilarCommand.render(library, options("nobody")) }
    }

    @Test
    fun `a seed whose profile has a non finite value cannot be placed`() {
        val broken = song("HH/A/bad.mp3", Float.NaN)

        val e = assertThrows(UsageException::class.java) { SimilarCommand.render(library + broken, options("bad")) }

        assertTrue("sound vector" in e.message.orEmpty())
    }

    @Test
    fun `run reads the export file and prints the result`() {
        val file = dir.resolve("p.jsonl")
        val lines =
            listOf(ProfileExportCodec.headerLine(ExportHeader(1, "Music"))) + library.map(ProfileExportCodec::songLine)
        Files.write(file, lines)
        val bytes = ByteArrayOutputStream()

        val code = SimilarCommand(PrintStream(bytes, true, "UTF-8")).run(SimilarOptions(file, "HH/A/seed", 2))

        assertEquals(0, code)
        assertTrue(bytes.toString("UTF-8").startsWith("seed: HH/A/seed.mp3\n"))
    }

    @Test
    fun `a file that is not an export is a usage error`() {
        val file = dir.resolve("p.jsonl")
        Files.writeString(file, "hello\n")

        assertThrows(UsageException::class.java) {
            SimilarCommand(PrintStream(ByteArrayOutputStream())).run(SimilarOptions(file, "x", 2))
        }
    }

    @Test
    fun `an isolated seed gets 3 of its 10 slots from its own and featured artists, read from the file names`() {
        // Centroids stand for positions on a line: the seed is alone at the low end, a dense group of 20 songs is
        // the nearest sound, and the seed's artist is farther on (a guest song featuring it, then its own songs).
        val group = (0 until 20).map { song("HH/C/Other - c${it + 1}.mp3", 10f + it * 0.1f) }
        val own =
            listOf(
                song("HH/Z/Guest - Duet feat. Seed Artist.mp3", 30f),
                song("HH/Y/Seed Artist - Own1.mp3", 31f),
                song("HH/Y/Seed Artist - Own2.mp3", 32f),
                song("HH/Y/Seed Artist - Own3.mp3", 33f),
                song("HH/Y/Seed Artist - Own4.mp3", 34f),
            )
        val records = group + song("HH/Y/Seed Artist - Hit.mp3", 0f) + own
        val guard = IsolationGuard(IsolationGuardConfig(rank = 2))

        val text = SimilarCommand.render(records, options("Hit", k = 10), guard = guard)

        val names =
            text
                .lines()
                .filter { it.contains("d=") }
                .drop(1)
                .map { it.substringAfterLast('/') }
        val expected =
            (1..7).map { "Other - c$it.mp3" } +
                listOf("Guest - Duet feat. Seed Artist.mp3", "Seed Artist - Own1.mp3", "Seed Artist - Own2.mp3")
        assertEquals(expected, names)
    }
}
