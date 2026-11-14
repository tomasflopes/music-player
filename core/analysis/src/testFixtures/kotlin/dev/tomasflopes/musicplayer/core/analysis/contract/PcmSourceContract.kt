package dev.tomasflopes.musicplayer.core.analysis.contract

import dev.tomasflopes.musicplayer.core.analysis.MonoDownmix
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * What every [PcmSource] must do on the generated MP3s (ADR 0053): right format and length, the same loudness per
 * window as the lossless source, a click where the reference says (the decoder offset), and a clean failure on a
 * missing file. The checks are plain code with no test framework, so JUnit 5 (`check`, ffmpeg) and JUnit 4
 * (`deviceCheck`, MediaCodec) run the same ones.
 */
object PcmSourceContract {
    /** Longest decoded length that may differ from the source (encoder padding, VBR estimates). */
    const val LENGTH_TOLERANCE_MS = 100L

    /** How far the first click may sit from where the source has it: the decoder offset. */
    const val ONSET_TOLERANCE_MS = 50L

    /** How far a seek may land from the request. */
    const val SEEK_TOLERANCE_MS = 150L

    private const val RMS_RELATIVE_TOLERANCE = 0.15
    private const val RMS_ABSOLUTE_TOLERANCE = 0.01
    private const val ONSET_LEVEL = 0.2f

    fun checks(
        source: PcmSource,
        fixtures: ContractFixtures,
    ): List<ContractCheck> {
        val reference = ReferenceTable.parse(fixtures.referenceText())
        val perFile = reference.flatMap { file -> checksFor(source, fixtures.location(file.name), file) }
        return perFile + ContractCheck("a missing file fails with PcmDecodeException") { missing(source, fixtures) }
    }

    private fun checksFor(
        source: PcmSource,
        location: String,
        file: ReferenceFile,
    ): List<ContractCheck> =
        buildList {
            add(named(file, "format and length") { formatAndLength(source, location, file) })
            add(named(file, "loudness after a full decode") { fullDecode(source, location, file) })
            add(named(file, "loudness after a seek") { seekDecode(source, location, file) })
            if (file.onsetMs >= 0) add(named(file, "first click offset") { onset(source, location, file) })
        }

    private fun named(
        file: ReferenceFile,
        what: String,
        run: () -> Unit,
    ) = ContractCheck("${file.name}: $what", run)

    private fun formatAndLength(
        source: PcmSource,
        location: String,
        file: ReferenceFile,
    ) {
        val info = source.info(location)
        verify(info.sampleRate == file.sampleRate && info.channels == file.channels) { "info: $info" }
        verify(abs(info.durationMs - file.durationMs) <= LENGTH_TOLERANCE_MS) { "info duration ${info.durationMs} ms" }
        val pcm = source.decodeAll(location)
        verify(pcm.sampleRate == file.sampleRate && pcm.channels == file.channels) {
            "decoded ${pcm.sampleRate} Hz, ${pcm.channels} ch"
        }
        verify(abs(pcm.durationMs - file.durationMs) <= LENGTH_TOLERANCE_MS) { "decoded ${pcm.durationMs} ms" }
    }

    private fun fullDecode(
        source: PcmSource,
        location: String,
        file: ReferenceFile,
    ) {
        val mono = MonoDownmix.toMono(source.decodeAll(location))
        for (window in file.windows) {
            val actual = rms(mono.window(window.startMs, window.lengthMs))
            expectRms(actual, window.monoRms, "window at ${window.startMs} ms")
        }
    }

    private fun seekDecode(
        source: PcmSource,
        location: String,
        file: ReferenceFile,
    ) {
        for (window in file.windows) {
            val pcm = source.decodeWindow(location, window.startMs, window.lengthMs)
            val label = "seek to ${window.startMs} ms"
            verify(abs(pcm.startMs - window.startMs) <= SEEK_TOLERANCE_MS) { "$label landed at ${pcm.startMs} ms" }
            verify(pcm.startMs + pcm.durationMs >= window.startMs + window.lengthMs - SEEK_TOLERANCE_MS) {
                "$label returned only ${pcm.durationMs} ms from ${pcm.startMs} ms"
            }
            val mono = MonoDownmix.toMono(pcm)
            expectRms(rms(mono.window(window.startMs, window.lengthMs)), window.monoRms, label)
        }
    }

    private fun onset(
        source: PcmSource,
        location: String,
        file: ReferenceFile,
    ) {
        val mono = MonoDownmix.toMono(source.decodeAll(location))
        val index = mono.samples.indexOfFirst { abs(it) > ONSET_LEVEL }
        verify(index >= 0) { "no click found" }
        val onsetMs = mono.startMs + index * MS_PER_SECOND / mono.sampleRate
        verify(abs(onsetMs - file.onsetMs) <= ONSET_TOLERANCE_MS) {
            "first click at $onsetMs ms, source has it at ${file.onsetMs} ms"
        }
    }

    private fun missing(
        source: PcmSource,
        fixtures: ContractFixtures,
    ) {
        val failure = runCatching { source.decodeAll(fixtures.missingLocation()) }.exceptionOrNull()
        verify(failure is PcmDecodeException) { "expected PcmDecodeException, got $failure" }
    }

    private fun expectRms(
        actual: Double,
        expected: Double,
        label: String,
    ) {
        val allowed = RMS_RELATIVE_TOLERANCE * expected + RMS_ABSOLUTE_TOLERANCE
        verify(abs(actual - expected) <= allowed) { "$label: rms $actual, source $expected (allowed $allowed)" }
    }

    private fun rms(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in samples) sum += sample * sample.toDouble()
        return sqrt(sum / samples.size)
    }

    private fun verify(
        condition: Boolean,
        message: () -> String,
    ) {
        if (!condition) throw AssertionError(message())
    }

    private const val MS_PER_SECOND = 1_000L
}
