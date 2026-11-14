package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.PcmBuffer
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import dev.tomasflopes.musicplayer.core.analysis.PcmInfo
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import java.io.IOException
import java.nio.file.Files
import java.util.Locale

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

    private fun decode(
        location: String,
        startMs: Long,
        lengthMs: Long?,
    ): PcmBuffer {
        val seek = if (startMs > 0L) listOf("-ss", seconds(startMs)) else emptyList()
        val length = if (lengthMs != null) listOf("-t", seconds(lengthMs)) else emptyList()
        val input = listOf("-i", location, "-vn", "-map", "0:a:0", "-map_metadata", "-1")
        val output = listOf("-c:a", "pcm_f32le", "-f", "wav", "pipe:1")
        val command = listOf(ffmpeg, "-v", "error", "-nostdin") + seek + length + input + output
        return WavReader.read(run(command), startMs)
    }

    /** Runs [command] and returns its stdout; stderr goes to a temp file so a full pipe cannot stall the process. */
    private fun run(command: List<String>): ByteArray {
        val errors = Files.createTempFile("analyser-ffmpeg", ".err")
        try {
            val process = ProcessBuilder(command).redirectError(errors.toFile()).start()
            val output = process.inputStream.readAllBytes()
            val exit = process.waitFor()
            if (exit != 0) {
                val reason =
                    Files
                        .readString(errors)
                        .trim()
                        .lineSequence()
                        .lastOrNull()
                        .orEmpty()
                throw PcmDecodeException("${command.first()} exited with $exit: $reason")
            }
            return output
        } catch (e: IOException) {
            throw PcmDecodeException("Could not run ${command.first()} (is ffmpeg installed?): ${e.message}", e)
        } finally {
            Files.deleteIfExists(errors)
        }
    }

    private fun seconds(ms: Long): String = "%.3f".format(Locale.ROOT, ms / MS_PER_SECOND)

    private companion object {
        const val MS_PER_SECOND = 1_000.0
    }
}
