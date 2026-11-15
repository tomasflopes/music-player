package dev.tomasflopes.musicplayer.tools.analyser

import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer

/** Lists the `.mp3` files under a music folder, keyed by their NFC-normalised path relative to it (ADR 0057). */
object Mp3Finder {
    fun find(root: Path): Map<String, Path> {
        require(Files.isDirectory(root)) { "Not a folder: $root" }
        val found = mutableMapOf<String, Path>()
        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && isMp3(it.fileName.toString()) }
                .forEach { found[relative(root, it)] = it }
        }
        return found
    }

    /** Skips macOS `._name.mp3` helper files that FAT/exFAT cards get next to every copied file. */
    private fun isMp3(name: String): Boolean = name.endsWith(".mp3", ignoreCase = true) && !name.startsWith("._")

    private fun relative(
        root: Path,
        file: Path,
    ): String {
        val path = root.relativize(file).joinToString("/") { it.toString() }
        return Normalizer.normalize(path, Normalizer.Form.NFC)
    }
}
