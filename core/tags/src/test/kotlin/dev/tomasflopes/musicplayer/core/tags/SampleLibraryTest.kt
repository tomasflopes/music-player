package dev.tomasflopes.musicplayer.core.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

class SampleLibraryTest {
    @TempDir
    lateinit var dir: File

    private fun source(sample: SampleFile) = ByteArrayTagSource(File(dir, sample.path).readBytes())

    @BeforeEach
    fun writeTheSamples() {
        SampleLibraryGenerator.writeTo(dir)
    }

    @Test
    fun `the reader returns exactly the expected tags for every generated file`() {
        val read =
            SampleLibrary.files.associate { sample ->
                sample.path to (Id3TagReader().read(source(sample)) as? TagReadResult.Success)?.tags
            }

        assertEquals(SampleLibrary.files.associate { it.path to it.expected }, read)
    }

    @Test
    fun `about thirty files, each with its own path`() {
        val paths = SampleLibrary.files.map { it.path.lowercase() }

        assertTrue(paths.size in 28..34, "${paths.size} files")
        assertEquals(paths.size, paths.toSet().size)
    }

    @Test
    fun `files in the root are under ROOT and exactly one file is outside it`() {
        val (inside, outside) = SampleLibrary.files.partition { it.inRoot }

        assertTrue(inside.all { it.path.startsWith(SampleLibrary.ROOT + "/") })
        assertEquals(listOf("Recordings/voice note.mp3"), outside.map { it.path })
    }

    @Test
    fun `every tag version is in the set`() {
        assertEquals(TagVersion.entries.toSet(), SampleLibrary.files.map { it.expected.version }.toSet())
    }

    @Test
    fun `two files carry a 64 by 64 orange PNG`() {
        val covers =
            SampleLibrary.files
                .filter { it.expected.hasCover }
                .map { checkNotNull(Id3CoverReader().readCover(source(it))) }
        val images = covers.map { ImageIO.read(ByteArrayInputStream(it.bytes)) }
        val orange = 0xFF8C00

        assertEquals(listOf("image/png", "image/png"), covers.map { it.mimeType })
        assertEquals(listOf(64 to 64, 64 to 64), images.map { it.width to it.height })
        assertEquals(listOf(orange, orange), images.map { it.getRGB(32, 32) and 0xFFFFFF })
    }

    @Test
    fun `each file is ten seconds of audio after its tag`() {
        val tenSeconds = SilentMp3.frames(10).size

        assertTrue(SampleLibrary.files.all { File(dir, it.path).length() >= tenSeconds })
    }
}
