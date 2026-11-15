package dev.tomasflopes.musicplayer.core.analysis

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** First line of an export (ADR 0056, 0065). */
data class ExportHeader(
    val profilerVersion: Int,
    val musicRootHint: String,
)

/** One song of an export: [path] is NFC-normalised and relative to the music root (ADR 0057). */
class ExportRecord(
    val path: String,
    val size: Long,
    val durationMs: Long,
    val profile: SoundProfile,
)

/** A parsed export. [truncatedTail] says the last line was half written and was ignored. */
class ExportContents(
    val header: ExportHeader,
    val records: List<ExportRecord>,
    val truncatedTail: Boolean,
)

/** The export file is not a sound profile export. */
class ExportFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** JSON Lines: a header line, then one line per song (ADR 0056, 0065). Written with the JSON element API. */
object ProfileExportCodec {
    fun headerLine(header: ExportHeader): String =
        buildJsonObject {
            put("profiler_version", header.profilerVersion)
            put("music_root", header.musicRootHint)
            put("fixed_names", JsonArray(SoundProfile.FIXED_NAMES.map { JsonPrimitive(it) }))
        }.toString()

    fun songLine(record: ExportRecord): String =
        buildJsonObject {
            put("path", record.path)
            put("size", record.size)
            put("duration_ms", record.durationMs)
            put("fixed", JsonArray(record.profile.fixed.map { JsonPrimitive(it) }))
            put("energy", JsonArray(record.profile.energyCurve.map { JsonPrimitive(it) }))
        }.toString()

    /** Throws [ExportFormatException] when the header is missing or a line before the last cannot be read. */
    fun read(lines: List<String>): ExportContents {
        val content = lines.filter { it.isNotBlank() }
        if (content.isEmpty()) throw ExportFormatException("The file is empty")
        val header = parseHeader(content.first())
        val records = mutableListOf<ExportRecord>()
        var truncated = false
        for ((index, line) in content.drop(1).withIndex()) {
            val record = parseSong(line)
            when {
                record != null -> records += record
                index == content.size - 2 -> truncated = true
                else -> throw ExportFormatException("Line ${index + 2} is not a song line")
            }
        }
        return ExportContents(header, records, truncated)
    }

    private fun parseHeader(line: String): ExportHeader =
        try {
            val json = Json.parseToJsonElement(line).jsonObject
            val names = json.getValue("fixed_names").jsonArray.map { it.jsonPrimitive.content }
            if (names != SoundProfile.FIXED_NAMES) throw ExportFormatException("The feature names do not match")
            ExportHeader(
                json.getValue("profiler_version").jsonPrimitive.int,
                json.getValue("music_root").jsonPrimitive.content,
            )
        } catch (e: SerializationException) {
            throw ExportFormatException("The first line is not an export header", e)
        } catch (e: IllegalArgumentException) {
            throw ExportFormatException("The first line is not an export header", e)
        } catch (e: NoSuchElementException) {
            throw ExportFormatException("The first line is not an export header", e)
        }

    /** The song on [line], or null when the line is not a complete song line. */
    private fun parseSong(line: String): ExportRecord? =
        try {
            val json: JsonObject = Json.parseToJsonElement(line).jsonObject
            val fixed =
                json
                    .getValue("fixed")
                    .jsonArray
                    .map { it.jsonPrimitive.float }
                    .toFloatArray()
            val energy =
                json
                    .getValue("energy")
                    .jsonArray
                    .map { it.jsonPrimitive.float }
                    .toFloatArray()
            ExportRecord(
                path = json.getValue("path").jsonPrimitive.content,
                size = json.getValue("size").jsonPrimitive.long,
                durationMs = json.getValue("duration_ms").jsonPrimitive.long,
                profile = SoundProfile(fixed, energy),
            )
        } catch (ignored: SerializationException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        } catch (ignored: NoSuchElementException) {
            null
        }
}
