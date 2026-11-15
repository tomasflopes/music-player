package dev.tomasflopes.musicplayer.tools.analyser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class Mp3FinderTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `ignores macOS helper files and other extensions`() {
        Files.createDirectories(root.resolve("a"))
        Files.write(root.resolve("a/song.mp3"), byteArrayOf(1))
        Files.write(root.resolve("a/Other.MP3"), byteArrayOf(1))
        Files.write(root.resolve("a/._song.mp3"), byteArrayOf(1))
        Files.write(root.resolve("a/cover.jpg"), byteArrayOf(1))

        assertEquals(setOf("a/song.mp3", "a/Other.MP3"), Mp3Finder.find(root).keys)
    }
}
