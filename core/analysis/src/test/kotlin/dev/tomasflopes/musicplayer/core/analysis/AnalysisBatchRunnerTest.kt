package dev.tomasflopes.musicplayer.core.analysis

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnalysisBatchRunnerTest {
    private var now = 0L
    private val clock = AnalysisClock { now }

    /** Every decode "takes" one second of fake time; [broken] locations fail like an undecodable file. */
    private inner class FakeSource(
        private val broken: Set<String> = emptySet(),
    ) : PcmSource {
        val decoded = mutableListOf<String>()

        override fun info(location: String) = PcmInfo(SAMPLE_RATE, 1, 1_000L)

        override fun decodeAll(location: String) = error("The runner streams")

        override fun decodeWindow(
            location: String,
            startMs: Long,
            lengthMs: Long,
        ) = error("The runner streams")

        override fun decodeStream(
            location: String,
            sink: PcmChunkSink,
        ) {
            decoded += location
            now += DECODE_MS
            if (location in broken) throw PcmDecodeException("Cannot decode $location")
            sink.begin(SAMPLE_RATE, 1)
            sink.accept(FloatArray(SAMPLE_RATE), SAMPLE_RATE)
        }
    }

    private class FakeProfiler(
        private val failOn: Int = -1,
    ) : SoundProfiler {
        override val version = 1
        private var streams = 0

        override fun newStream(sampleRate: Int): ProfileStream {
            val number = streams++
            return object : ProfileStream {
                override fun feed(
                    samples: FloatArray,
                    count: Int,
                ) = check(number != failOn) { "profiler broke on stream $number" }

                override fun finish() = SoundProfile(FloatArray(SoundProfile.FIXED_SIZE), FloatArray(0))
            }
        }
    }

    /** The queue and the store share the state, like the two Room-backed ports do. */
    private class FakeLibrary(
        songs: Int,
        private val ignoresAttempts: Boolean = false,
    ) : AnalysisQueue,
        AnalysisStore {
        private val all = (1..songs).map { AnalysisTarget(it.toLong(), "song-$it", it * 100L, 0) }.toMutableList()
        val saved = mutableListOf<AnalysisTarget>()
        val failures = mutableMapOf<Long, Pair<Int, String>>()

        private fun attempts(id: Long) = failures[id]?.first ?: 0

        val waiting: Int get() = all.count { it.songId !in saved.map { s -> s.songId } && attempts(it.songId) < 3 }

        override suspend fun next(
            limit: Int,
            skip: Set<Long>,
        ): List<AnalysisTarget> =
            all
                .filter { t -> saved.none { it.songId == t.songId } && t.songId !in skip }
                .filter { ignoresAttempts || attempts(it.songId) < AnalysisBatchRunner.MAX_ATTEMPTS }
                .map { it.copy(attempts = attempts(it.songId)) }
                .take(limit)

        override suspend fun saveProfile(
            target: AnalysisTarget,
            profile: SoundProfile,
        ) {
            saved += target
        }

        override suspend fun recordFailure(
            target: AnalysisTarget,
            message: String,
        ) {
            failures[target.songId] = attempts(target.songId) + 1 to message
        }
    }

    private fun runner(
        library: FakeLibrary,
        source: FakeSource = FakeSource(),
        profiler: SoundProfiler = FakeProfiler(),
        batchSize: Int = 2,
    ) = AnalysisBatchRunner(source, profiler, library, library, clock, batchSize)

    @Test
    fun `an empty queue is finished at once`() =
        runTest {
            val result = runner(FakeLibrary(0)).run(deadlineMs = HOUR)

            assertEquals(BatchResult(0, 0, finished = true), result)
        }

    @Test
    fun `every queued song gets a profile with the file size of its row`() =
        runTest {
            val library = FakeLibrary(5)

            val result = runner(library).run(deadlineMs = HOUR)

            assertEquals(BatchResult(5, 0, finished = true), result)
            assertEquals(listOf(100L, 200L, 300L, 400L, 500L), library.saved.map { it.fileSize })
        }

    @Test
    fun `the deadline stops the run between songs and the next run carries on`() =
        runTest {
            val library = FakeLibrary(5)

            val first = runner(library).run(deadlineMs = 2_500L)

            // Songs start at 0, 1 and 2 seconds; at 3 seconds the deadline has passed.
            assertEquals(BatchResult(3, 0, finished = false), first)
            val second = runner(library).run(deadlineMs = HOUR)
            assertEquals(BatchResult(2, 0, finished = true), second)
            assertEquals(5, library.saved.size)
        }

    @Test
    fun `a deadline that has already passed does no work`() =
        runTest {
            val source = FakeSource()

            val result = runner(FakeLibrary(3), source).run(deadlineMs = 0L)

            assertEquals(BatchResult(0, 0, finished = false), result)
            assertTrue(source.decoded.isEmpty())
        }

    @Test
    fun `a song that cannot be decoded is recorded and the others still run`() =
        runTest {
            val library = FakeLibrary(3)
            val source = FakeSource(broken = setOf("song-2"))

            val result = runner(library, source).run(deadlineMs = HOUR)

            assertEquals(BatchResult(2, 1, finished = true), result)
            assertEquals(1 to "Cannot decode song-2", library.failures[2L])
            assertEquals(1, source.decoded.count { it == "song-2" }, "tried once in one run")
        }

    @Test
    fun `a profiler error counts as a failed song too`() =
        runTest {
            val library = FakeLibrary(2)

            val result = runner(library, profiler = FakeProfiler(failOn = 0)).run(deadlineMs = HOUR)

            assertEquals(BatchResult(1, 1, finished = true), result)
            assertEquals("profiler broke on stream 0", library.failures[1L]?.second)
        }

    @Test
    fun `a song is given up after three attempts`() =
        runTest {
            val library = FakeLibrary(2)
            val source = FakeSource(broken = setOf("song-1"))

            repeat(3) { runner(library, source).run(deadlineMs = HOUR) }
            val fourth = runner(library, source).run(deadlineMs = HOUR)

            assertEquals(3, library.failures[1L]?.first)
            assertEquals(3, source.decoded.count { it == "song-1" })
            assertEquals(BatchResult(0, 0, finished = true), fourth)
        }

    @Test
    fun `a queue that still offers a given-up song does not get it tried again`() =
        runTest {
            val library = FakeLibrary(1, ignoresAttempts = true)
            val source = FakeSource(broken = setOf("song-1"))

            repeat(AnalysisBatchRunner.MAX_ATTEMPTS) { runner(library, source).run(deadlineMs = HOUR) }
            val result = runner(library, source).run(deadlineMs = HOUR)

            assertEquals(AnalysisBatchRunner.MAX_ATTEMPTS, source.decoded.size)
            assertEquals(0, result.failed)
            assertFalse(library.saved.isNotEmpty())
        }

    private companion object {
        const val SAMPLE_RATE = 8_000
        const val DECODE_MS = 1_000L
        const val HOUR = 3_600_000L
    }
}
