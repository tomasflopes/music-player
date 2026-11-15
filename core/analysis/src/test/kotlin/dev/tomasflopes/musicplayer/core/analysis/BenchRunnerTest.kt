package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.core.analysis.config.BenchRules
import dev.tomasflopes.musicplayer.core.analysis.config.SpectrumConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BenchRunnerTest {
    private class FakeSource(
        private val sampleRate: Int,
        private val seconds: Int,
    ) : PcmSource {
        val windowRequests = mutableListOf<Pair<Long, Long>>()

        override fun info(location: String) = PcmInfo(sampleRate, 1, seconds * 1_000L)

        override fun decodeAll(location: String) = PcmBuffer(sampleRate, 1, 0L, FloatArray(sampleRate * seconds))

        override fun decodeWindow(
            location: String,
            startMs: Long,
            lengthMs: Long,
        ): PcmBuffer {
            windowRequests += startMs to lengthMs
            return PcmBuffer(sampleRate, 1, startMs, FloatArray((sampleRate * lengthMs / 1_000L).toInt()))
        }

        override fun decodeStream(
            location: String,
            sink: PcmChunkSink,
        ) {
            val pcm = decodeAll(location)
            sink.begin(pcm.sampleRate, pcm.channels)
            sink.accept(pcm.samples, pcm.samples.size)
        }
    }

    private var now = 0L
    private val clock = { now.also { now += 1_000_000L } }

    @Test
    fun `a song is timed in every stage and the windows are the planned ones`() {
        val source = FakeSource(sampleRate = 8_000, seconds = 100)

        val timings = BenchRunner(source, nanoTime = clock).measure("song.mp3")

        assertEquals(8_000, timings.sampleRate)
        assertTrue(timings.fullDecodeNs > 0 && timings.seekDecodeNs > 0)
        assertEquals(SpectrumConfig.SPIKE_VARIANTS.toSet(), timings.spectrumNs.keys)
        assertEquals(listOf(15_000L, 40_000L, 65_000L), source.windowRequests.map { it.first })
    }

    @Test
    fun `the report states the rules and an estimate`() {
        val source = FakeSource(sampleRate = 8_000, seconds = 100)
        val timings = List(3) { BenchRunner(source, nanoTime = clock).measure("song.mp3") }
        val run = BenchRun("1 thread", parallelism = 1, wallNs = timings.sumOf { it.totalNs }, timings = timings)

        val text =
            BenchReport.format(
                BenchInput(
                    seed = 1L,
                    failed = 0,
                    sampleRates = SampleRateCount.of(List(98) { 44_100 } + List(2) { 48_000 }),
                    runs = listOf(run),
                    rules = BenchRules(librarySongs = 16_000),
                ),
            )

        assertTrue(text.contains("3 songs"))
        assertTrue(text.contains("44100 Hz 98.0 %"))
        assertTrue(text.contains("no resampling"))
        assertTrue(text.contains("Estimate for 16,000 songs"))
    }
}
