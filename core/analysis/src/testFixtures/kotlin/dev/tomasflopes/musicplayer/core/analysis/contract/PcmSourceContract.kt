package dev.tomasflopes.musicplayer.core.analysis.contract

import dev.tomasflopes.musicplayer.core.analysis.HandBuiltProfilerV1
import dev.tomasflopes.musicplayer.core.analysis.MonoDownmix
import dev.tomasflopes.musicplayer.core.analysis.PcmBuffer
import dev.tomasflopes.musicplayer.core.analysis.PcmChunkSink
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import dev.tomasflopes.musicplayer.core.analysis.analyse
import dev.tomasflopes.musicplayer.core.analysis.analyseStream
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
        return perFile +
            ContractCheck("a missing file fails with PcmDecodeException") { missing(source, fixtures) } +
            ContractCheck("streaming a missing file fails with PcmDecodeException") { missingStream(source, fixtures) }
    }

    private fun checksFor(
        source: PcmSource,
        location: String,
        file: ReferenceFile,
    ): List<ContractCheck> =
        buildList {
            add(named(file, "format and length") { formatAndLength(source, location, file) })
            add(named(file, "loudness after a full decode") { fullDecode(source, location, file) })
            add(named(file, "stream chunks and format") { streamShape(source, location, file) })
            add(named(file, "loudness after a stream") { streamLoudness(source, location, file) })
            add(named(file, "streamed profile equals the whole-file profile") { streamProfile(source, location) })
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

    /** Records what a stream delivers: the format, the chunk sizes and the samples, in order. */
    private class Recorder : PcmChunkSink {
        var begins = 0
        var sampleRate = 0
        var channels = 0
        var chunks = 0
        var largest = 0
        var misaligned = 0
        var acceptedBeforeBegin = false
        val samples = ArrayList<Float>()

        override fun begin(
            sampleRate: Int,
            channels: Int,
        ) {
            begins++
            this.sampleRate = sampleRate
            this.channels = channels
        }

        override fun accept(
            samples: FloatArray,
            count: Int,
        ) {
            if (begins == 0) acceptedBeforeBegin = true
            chunks++
            largest = maxOf(largest, count)
            if (channels > 0 && count % channels != 0) misaligned++
            for (i in 0 until count) this.samples.add(samples[i])
        }

        fun toBuffer() = PcmBuffer(sampleRate, channels, 0L, samples.toFloatArray())
    }

    private fun streamShape(
        source: PcmSource,
        location: String,
        file: ReferenceFile,
    ) {
        val recorder = Recorder()
        source.decodeStream(location, recorder)
        verify(recorder.begins == 1) { "begin was called ${recorder.begins} times" }
        verify(!recorder.acceptedBeforeBegin) { "a chunk arrived before begin" }
        verify(recorder.sampleRate == file.sampleRate && recorder.channels == file.channels) {
            "streamed ${recorder.sampleRate} Hz, ${recorder.channels} ch"
        }
        verify(recorder.misaligned == 0) { "${recorder.misaligned} chunks split a frame" }
        verify(recorder.largest <= PcmChunkSink.MAX_CHUNK_SAMPLES) { "a chunk of ${recorder.largest} values" }
        val duration = recorder.toBuffer().durationMs
        verify(abs(duration - file.durationMs) <= LENGTH_TOLERANCE_MS) { "streamed $duration ms" }
    }

    private fun streamLoudness(
        source: PcmSource,
        location: String,
        file: ReferenceFile,
    ) {
        val recorder = Recorder()
        source.decodeStream(location, recorder)
        val mono = MonoDownmix.toMono(recorder.toBuffer())
        for (window in file.windows) {
            expectRms(
                rms(mono.window(window.startMs, window.lengthMs)),
                window.monoRms,
                "window at ${window.startMs} ms",
            )
        }
    }

    /** The same decoder gives the same samples either way, and the profile does not depend on the chunking. */
    private fun streamProfile(
        source: PcmSource,
        location: String,
    ) {
        val profiler = HandBuiltProfilerV1()
        val whole = profiler.analyse(source.decodeAll(location))
        val streamed = profiler.analyseStream(source, location)
        verify(whole == streamed) { "the streamed profile differs from the whole-file profile" }
    }

    private fun missingStream(
        source: PcmSource,
        fixtures: ContractFixtures,
    ) {
        val failure = runCatching { source.decodeStream(fixtures.missingLocation(), Recorder()) }.exceptionOrNull()
        verify(failure is PcmDecodeException) { "expected PcmDecodeException, got $failure" }
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
