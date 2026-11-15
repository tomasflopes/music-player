package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.ExportFormatException
import dev.tomasflopes.musicplayer.core.analysis.ExportHeader
import dev.tomasflopes.musicplayer.core.analysis.ExportRecord
import dev.tomasflopes.musicplayer.core.analysis.ProfileExportCodec
import java.io.BufferedWriter
import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * The export file of a run (ADR 0056, 0065): opens or resumes it and appends one line per finished song, flushed at
 * once, so Ctrl-C loses at most the song being written.
 */
class ProfileFile(
    private val path: Path,
    private val profilerVersion: Int,
    private val musicRootHint: String,
) : Closeable {
    private var writer: BufferedWriter? = null

    /**
     * Starts a new file, or resumes the one at [path] and returns the songs already in it (relative path to size).
     * A half-written last line is cut off. A file of another profiler version stops the run.
     */
    fun open(): Map<String, Long> {
        val done = if (Files.exists(path) && Files.size(path) > 0L) resume() else start()
        writer =
            Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        return done
    }

    @Synchronized
    fun append(record: ExportRecord) {
        val out = checkNotNull(writer) { "The file is not open" }
        out.write(ProfileExportCodec.songLine(record))
        out.newLine()
        out.flush()
    }

    override fun close() {
        writer?.close()
    }

    private fun start(): Map<String, Long> {
        Files.writeString(path, ProfileExportCodec.headerLine(ExportHeader(profilerVersion, musicRootHint)) + "\n")
        return emptyMap()
    }

    private fun resume(): Map<String, Long> {
        val text = Files.readString(path, StandardCharsets.UTF_8)
        val lines = text.lines().filter { it.isNotBlank() }
        val contents =
            try {
                ProfileExportCodec.read(lines)
            } catch (e: ExportFormatException) {
                throw UsageException("$path is not a profile export: ${e.message}", e)
            }
        if (contents.header.profilerVersion != profilerVersion) {
            throw UsageException(
                "$path was written by profiler version ${contents.header.profilerVersion}, this is $profilerVersion. " +
                    "Use another --out or delete the file.",
            )
        }
        if (contents.truncatedTail || !text.endsWith("\n")) {
            val kept = if (contents.truncatedTail) lines.dropLast(1) else lines
            Files.writeString(path, kept.joinToString("\n", postfix = "\n"))
        }
        return contents.records.associate { it.path to it.size }
    }
}
