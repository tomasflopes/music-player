package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class ProfilingSinkTest {
    private val rate = 44_100
    private val stereo =
        PcmBuffer(
            sampleRate = rate,
            channels = 2,
            startMs = 0L,
            samples =
                FloatArray(rate * 2 * 4) { i ->
                    val t = (i / 2).toDouble() / rate
                    val tone = if (i % 2 == 0) 440.0 else 660.0
                    (0.4 * sin(2 * PI * tone * t) * (1.0 + sin(2 * PI * 2.0 * t)) / 2).toFloat()
                },
        )

    private class ChunkedSource(
        private val pcm: PcmBuffer,
        private val chunkFrames: Int,
    ) : PcmSource {
        override fun info(location: String) = PcmInfo(pcm.sampleRate, pcm.channels, pcm.durationMs)

        override fun decodeAll(location: String) = pcm

        override fun decodeWindow(
            location: String,
            startMs: Long,
            lengthMs: Long,
        ) = pcm

        override fun decodeStream(
            location: String,
            sink: PcmChunkSink,
        ) {
            sink.begin(pcm.sampleRate, pcm.channels)
            val step = chunkFrames * pcm.channels
            var from = 0
            while (from < pcm.samples.size) {
                val count = minOf(step, pcm.samples.size - from)
                sink.accept(pcm.samples.copyOfRange(from, from + count), count)
                from += count
            }
        }
    }

    @Test
    fun `a streamed stereo song gives the profile of the whole buffer, whatever the chunk size`() {
        val profiler = HandBuiltProfilerV1()
        val whole = profiler.analyse(stereo)

        for (frames in listOf(1, 441, 4_096, 100_000)) {
            val streamed = profiler.analyseStream(ChunkedSource(stereo, frames), "song")
            assertEquals(whole, streamed, "chunks of $frames frames")
        }
    }

    @Test
    fun `finishing before any audio arrived is an error`() {
        assertThrows(IllegalStateException::class.java) { ProfilingSink(HandBuiltProfilerV1()).finish() }
    }

    @Test
    fun `a chunk before begin is an error`() {
        val sink = ProfilingSink(HandBuiltProfilerV1())

        assertThrows(IllegalStateException::class.java) { sink.accept(FloatArray(2), 2) }
    }
}
