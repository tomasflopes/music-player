package dev.tomasflopes.musicplayer.core.tags.audit

import dev.tomasflopes.musicplayer.core.tags.Id3TagReader
import dev.tomasflopes.musicplayer.core.tags.TagReadResult
import dev.tomasflopes.musicplayer.core.tags.TagReader
import dev.tomasflopes.musicplayer.core.tags.TagSource
import dev.tomasflopes.musicplayer.core.tags.TagVersion
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/** One audited file: its path relative to the audited folder, and what the reader made of it. */
internal data class AuditEntry(
    val path: String,
    val result: TagReadResult,
)

/** A real file on the Mac. Exercises the same seek-and-skip path the phone will use. */
internal class FileTagSource(
    private val file: File,
) : TagSource {
    override val fileName: String get() = file.name

    override fun openStream(): InputStream = FileInputStream(file)

    override fun readTail(byteCount: Int): ByteArray? =
        RandomAccessFile(file, "r").use { access ->
            if (access.length() < byteCount) {
                null
            } else {
                access.seek(access.length() - byteCount)
                ByteArray(byteCount).also(access::readFully)
            }
        }
}

/** Numbers that say whether the hand-written reader can be trusted with a real library (ADR 0006, layer 3). */
internal class AuditReport(
    val entries: List<AuditEntry>,
    private val seconds: Double,
) {
    private val read = entries.mapNotNull { it.result as? TagReadResult.Success }

    val failures: List<AuditEntry> = entries.filter { it.result is TagReadResult.Failure }
    val versionCounts: Map<TagVersion, Int> = read.groupingBy { it.tags.version }.eachCount()
    val warningCounts: Map<String, Int> = read.flatMap { it.warnings }.groupingBy { it }.eachCount()
    val missingTitle = read.count { it.tags.title == null }
    val missingArtist = read.count { it.tags.artist == null }
    val missingAlbum = read.count { it.tags.album == null }

    /** Decision C8: the first value is used; this count tells the owner whether that is good enough. */
    val multiValueArtist = read.count { success -> success.warnings.any { it in MULTI_VALUE_ARTIST } }

    fun summary(): String =
        buildString {
            appendLine("Files read: ${entries.size}")
            appendLine("Seconds: ${"%.1f".format(seconds)}")
            appendLine("Files per second: ${if (seconds > 0) (entries.size / seconds).roundToInt() else "n/a"}")
            val versions = versionCounts.toSortedMap().entries.joinToString { "${it.key}=${it.value}" }
            appendLine("By tag version: $versions")
            appendLine("Missing title: $missingTitle")
            appendLine("Missing artist: $missingArtist")
            appendLine("Missing album: $missingAlbum")
            appendLine("Multi-value artist (C8): $multiValueArtist")
            appendLine("Warnings by kind: ${warningCounts.size}")
            warningCounts.entries.sortedByDescending { it.value }.forEach { appendLine("  ${it.key}: ${it.value}") }
            appendLine("Failures: ${failures.size}")
            failures.forEach { appendLine("  ${it.path}: ${(it.result as TagReadResult.Failure).reason}") }
            appendLine("Album checks (numbering, duplicates, order for -Palbums): added with plan Task 7.")
        }

    fun filesCsv(): String =
        buildString {
            appendLine(CSV_HEADER)
            entries.forEach { appendLine(csvRow(it).joinToString(",", transform = ::csvField)) }
        }

    private fun csvRow(entry: AuditEntry): List<Any?> =
        when (val result = entry.result) {
            is TagReadResult.Failure -> listOf(entry.path, "FAILED") + List(TAG_COLUMNS) { null } + result.reason
            is TagReadResult.Success ->
                with(result.tags) {
                    listOf(entry.path, version, title, artist, albumArtist, album) +
                        listOf(trackNumber, trackTotal, discNumber, discTotal, hasCover) +
                        result.warnings.joinToString("; ")
                }
        }

    private fun csvField(value: Any?): String {
        val text = value?.toString().orEmpty()
        return if (text.any { it in CSV_SPECIAL }) "\"${text.replace("\"", "\"\"")}\"" else text
    }

    private companion object {
        const val CSV_HEADER =
            "path,version,title,artist,album_artist,album,track,track_total,disc,disc_total,cover,problems"
        const val TAG_COLUMNS = 9
        const val CSV_SPECIAL = ",\"\r\n"
        val MULTI_VALUE_ARTIST = setOf("multi-value TPE1", "multi-value TP1")
    }
}

/** Runs [Id3TagReader] over a real music folder, read-only, and writes what it found to the build directory. */
internal object LibraryAudit {
    private const val NANOS_PER_SECOND = 1e9

    fun audit(
        root: File,
        reader: TagReader = Id3TagReader(),
    ): AuditReport {
        val files =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension.equals("mp3", ignoreCase = true) }
                .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
                .toList()
        val start = System.nanoTime()
        val entries =
            files.map { file ->
                AuditEntry(file.relativeTo(root).invariantSeparatorsPath, reader.read(FileTagSource(file)))
            }
        return AuditReport(entries, (System.nanoTime() - start) / NANOS_PER_SECOND)
    }

    /** Arguments: the music folder, then the output folder. Entry point of the `auditLibrary` Gradle task. */
    @JvmStatic
    fun main(args: Array<String>) {
        val (root, out) = args.take(2).map(::File)
        require(root.isDirectory) { "Not a folder: $root" }
        val report = audit(root)
        out.mkdirs()
        File(out, "summary.txt").writeText(report.summary())
        File(out, "files.csv").writeText(report.filesCsv())
        println(report.summary())
        println("Written to $out")
    }
}
