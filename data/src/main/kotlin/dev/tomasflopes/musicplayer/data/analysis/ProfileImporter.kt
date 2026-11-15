package dev.tomasflopes.musicplayer.data.analysis

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomasflopes.musicplayer.core.analysis.ExportFormatException
import dev.tomasflopes.musicplayer.core.analysis.ExportRecord
import dev.tomasflopes.musicplayer.core.analysis.HandBuiltProfilerV1
import dev.tomasflopes.musicplayer.core.analysis.ImportTarget
import dev.tomasflopes.musicplayer.core.analysis.ProfileBlobCodec
import dev.tomasflopes.musicplayer.core.analysis.ProfileExportCodec
import dev.tomasflopes.musicplayer.core.analysis.ProfileImportMatcher
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.dao.AnalysisSummaryRow
import dev.tomasflopes.musicplayer.data.db.entity.SoundProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/** The import cannot run as asked; the message is shown to the owner as it is. */
open class ProfileImportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The export was made by another profiler version (ADR 0056, 0065). Nothing is written. */
class ProfilerVersionMismatchException(
    val found: Int,
    val expected: Int,
) : ProfileImportException(
        "This export was made by profiler version $found, and this app reads version $expected. " +
            "Run the Mac analyser again so both use the same version.",
    )

/** What one import did. [matched] counts in-library songs only; [retiredMatched] is kept but not counted (ADR 0057). */
data class ProfileImportResult(
    val matched: Int,
    val retiredMatched: Int,
)

/**
 * Imports a Mac export (ADR 0056, 0057, 0065) through SAF. Reads the file one line at a time, so the 45 MB export
 * never sits in memory as one string. Matching is by NFC relative path and exact size. Importing twice writes the
 * same rows again, so nothing changes.
 */
@Singleton
class ProfileImporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val db: MusicDatabase,
        private val settings: SettingsRepository,
    ) {
        /** Songs with a profile for the current file and version, and the ones still queued (ADR 0058). */
        fun observeSummary(): Flow<AnalysisSummaryRow> =
            db.soundProfileDao().observeSummary(HandBuiltProfilerV1.VERSION)

        /** Imports the export at [uri]. [onProgress] gets the fraction of the file read, from 0 to 1. */
        suspend fun importFrom(
            uri: Uri,
            onProgress: (Float) -> Unit = {},
        ): ProfileImportResult =
            withContext(Dispatchers.IO) {
                val root =
                    settings.musicRoot.first()
                        ?: throw ProfileImportException("Choose your music folder first, then import the file.")
                val total = sizeOf(uri)
                val input =
                    context.contentResolver.openInputStream(uri)
                        ?: throw ProfileImportException("The file cannot be opened.")
                val counting =
                    CountingInputStream(input) { read -> onProgress((read.toFloat() / total).coerceAtMost(1f)) }
                try {
                    counting.use { stream ->
                        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                            importLines(reader, root.relativePath)
                        }
                    }
                } catch (e: ExportFormatException) {
                    throw ProfileImportException("This is not a sound profile export: ${e.message}", e)
                } catch (e: IOException) {
                    throw ProfileImportException("The file could not be read: ${e.message}", e)
                }
            }

        private suspend fun importLines(
            reader: BufferedReader,
            relativeRoot: String,
        ): ProfileImportResult {
            val header = firstHeader(reader)
            if (header.profilerVersion != HandBuiltProfilerV1.VERSION) {
                throw ProfilerVersionMismatchException(header.profilerVersion, HandBuiltProfilerV1.VERSION)
            }
            val dao = db.soundProfileDao()
            val matcher =
                ProfileImportMatcher(
                    dao.importTargets().asSequence().map { ImportTarget(it.id, it.path, it.fileSize, it.inLibrary) },
                    relativeRoot,
                )
            val batch = ArrayList<SoundProfileEntity>(BATCH_SIZE)
            var matched = 0
            var retired = 0
            var lineNumber = 1

            // A line is only known to be complete once the next one has been read: the last one may be half written.
            var pending: String? = null
            var pendingNumber = 0
            var line = reader.readLine()
            while (line != null) {
                lineNumber++
                if (line.isNotBlank()) {
                    pending?.let {
                        accept(it, pendingNumber, matcher, batch, header.profilerVersion) { counted ->
                            if (counted) matched++ else retired++
                        }
                    }
                    pending = line
                    pendingNumber = lineNumber
                    if (batch.size >= BATCH_SIZE) flush(batch)
                }
                line = reader.readLine()
            }
            // The last line: a half-written tail is ignored, a complete one is a song.
            pending?.let { last ->
                ProfileExportCodec.parseSong(last)?.let { record ->
                    stage(record, matcher, batch, header.profilerVersion) { counted ->
                        if (counted) matched++ else retired++
                    }
                }
            }
            flush(batch)
            return ProfileImportResult(matched, retired)
        }

        private fun firstHeader(reader: BufferedReader) =
            generateSequence { reader.readLine() }
                .firstOrNull { it.isNotBlank() }
                ?.let { ProfileExportCodec.parseHeader(it) }
                ?: throw ExportFormatException("The file is empty")

        private inline fun accept(
            line: String,
            number: Int,
            matcher: ProfileImportMatcher,
            batch: MutableList<SoundProfileEntity>,
            version: Int,
            onMatch: (Boolean) -> Unit,
        ) {
            val record =
                ProfileExportCodec.parseSong(line)
                    ?: throw ExportFormatException("Line $number is not a song line")
            stage(record, matcher, batch, version, onMatch)
        }

        private inline fun stage(
            record: ExportRecord,
            matcher: ProfileImportMatcher,
            batch: MutableList<SoundProfileEntity>,
            version: Int,
            onMatch: (Boolean) -> Unit,
        ) {
            val match = matcher.match(record.path, record.size) ?: return
            batch +=
                SoundProfileEntity(
                    songId = match.songId,
                    profilerVersion = version,
                    features = ProfileBlobCodec.encode(record.profile),
                    source = SOURCE_MAC,
                    fileSize = record.size,
                    analysedAt = System.currentTimeMillis(),
                )
            onMatch(match.counted)
        }

        private suspend fun flush(batch: MutableList<SoundProfileEntity>) {
            if (batch.isEmpty()) return
            val dao = db.soundProfileDao()
            db.withTransaction { batch.forEach { dao.upsert(it) } }
            batch.clear()
        }

        private fun sizeOf(uri: Uri): Long =
            context.contentResolver
                .query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L }
                ?.takeIf { it > 0 }
                ?: UNKNOWN_SIZE

        private companion object {
            const val BATCH_SIZE = 500
            const val SOURCE_MAC = "mac"
            const val UNKNOWN_SIZE = 1L
        }
    }

/** Counts the bytes read through it and reports the running total. */
private class CountingInputStream(
    input: InputStream,
    private val onRead: (Long) -> Unit,
) : FilterInputStream(input) {
    private var read = 0L

    override fun read(): Int = super.read().also { if (it >= 0) report(1) }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = super.read(buffer, offset, length).also { if (it > 0) report(it.toLong()) }

    private fun report(count: Long) {
        read += count
        onRead(read)
    }
}
