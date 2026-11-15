package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.PcmBuffer
import dev.tomasflopes.musicplayer.core.analysis.PcmChunkSink
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import dev.tomasflopes.musicplayer.core.analysis.PcmInfo
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Decodes through a separate `ffmpeg` process (ADR 0053): 32-bit float WAV on a pipe, at the file's own rate and
 * channel count. Seeking uses `-ss` before the input, which ffmpeg makes sample-accurate for MP3.
 */
class FfmpegPcmSource(
    private val ffmpeg: String = "ffmpeg",
    private val ffprobe: String = "ffprobe",
) : PcmSource {
    override fun info(location: String): PcmInfo {
        val stream = listOf(ffprobe, "-v", "error", "-select_streams", "a:0")
        val entries = listOf("-show_entries", "stream=sample_rate,channels:format=duration")
        val output = run(stream + entries + listOf("-of", "default=noprint_wrappers=1", location))
        val values =
            output
                .decodeToString()
                .lines()
                .map { it.split('=', limit = 2) }
                .filter { it.size == 2 }
                .associate { it[0].trim() to it[1].trim() }
        val sampleRate = values["sample_rate"]?.toIntOrNull()
        val channels = values["channels"]?.toIntOrNull()
        val seconds = values["duration"]?.toDoubleOrNull()
        if (sampleRate == null || channels == null || seconds == null) {
            throw PcmDecodeException("ffprobe found no audio stream in $location")
        }
        return PcmInfo(sampleRate, channels, (seconds * MS_PER_SECOND).toLong())
    }

    override fun decodeAll(location: String): PcmBuffer = decode(location, startMs = 0L, lengthMs = null)

    override fun decodeWindow(
        location: String,
        startMs: Long,
        lengthMs: Long,
    ): PcmBuffer = decode(location, startMs, lengthMs)

    override fun decodeStream(
        location: String,
        sink: PcmChunkSink,
    ) {
        val command = listOf(ffmpeg, "-v", "error", "-nostdin") + input(location) + output()
        runStreaming(command) { WavStreamReader.stream(it, sink) }
    }

    private fun input(location: String) = listOf("-i", location, "-vn", "-map", "0:a:0", "-map_metadata", "-1")

    private fun output() = listOf("-c:a", "pcm_f32le", "-f", "wav", "pipe:1")

    private fun decode(
        location: String,
        startMs: Long,
        lengthMs: Long?,
    ): PcmBuffer {
        val seek = if (startMs > 0L) listOf("-ss", seconds(startMs)) else emptyList()
        val length = if (lengthMs != null) listOf("-t", seconds(lengthMs)) else emptyList()
        val command = listOf(ffmpeg, "-v", "error", "-nostdin") + seek + length + input(location) + output()
        return WavReader.read(run(command), startMs)
    }

    /** Runs [command] and returns its stdout; stderr goes to a temp file so a full pipe cannot stall the process. */
    private fun run(command: List<String>): ByteArray {
        var output = ByteArray(0)
        runStreaming(command) { output = it.readAllBytes() }
        return output
    }

    /** Runs [command] and lets [consume] read its stdout as it comes. A failing exit is reported after [consume]. */
    private fun runStreaming(
        command: List<String>,
        consume: (InputStream) -> Unit,
    ) {
        val errors = Files.createTempFile("analyser-ffmpeg", ".err")
        try {
            val process = ProcessBuilder(command).redirectError(errors.toFile()).start()
            var finished = false
            try {
                val failure = readFault(command, process, errors, consume) ?: exitFault(command, process, errors)
                failure?.let { throw it }
                finished = true
            } finally {
                if (!finished) process.destroyForcibly()
            }
        } catch (e: IOException) {
            throw PcmDecodeException("Could not run ${command.first()} (is ffmpeg installed?): ${e.message}", e)
        } finally {
            Files.deleteIfExists(errors)
        }
    }

    /** What went wrong while reading, or null. If ffmpeg failed, its message says why; the reader saw a bare pipe. */
    private fun readFault(
        command: List<String>,
        process: Process,
        errors: Path,
        consume: (InputStream) -> Unit,
    ): PcmDecodeException? =
        try {
            process.inputStream.use(consume)
            null
        } catch (e: PcmDecodeException) {
            explained(command, process, errors) ?: e
        }

    private fun exitFault(
        command: List<String>,
        process: Process,
        errors: Path,
    ): PcmDecodeException? = failureOf(command, process.waitFor(), errors)

    private fun explained(
        command: List<String>,
        process: Process,
        errors: Path,
    ): PcmDecodeException? =
        if (process.waitFor(EXIT_WAIT_SECONDS, TimeUnit.SECONDS)) {
            failureOf(command, process.exitValue(), errors)
        } else {
            null
        }

    private fun failureOf(
        command: List<String>,
        exit: Int,
        errors: Path,
    ): PcmDecodeException? {
        if (exit == 0) return null
        val reason =
            Files
                .readString(errors)
                .trim()
                .lineSequence()
                .lastOrNull()
                .orEmpty()
        return PcmDecodeException("${command.first()} exited with $exit: $reason")
    }

    private fun seconds(ms: Long): String = "%.3f".format(Locale.ROOT, ms / MS_PER_SECOND)

    private companion object {
        const val MS_PER_SECOND = 1_000.0
        const val EXIT_WAIT_SECONDS = 1L
    }
}
