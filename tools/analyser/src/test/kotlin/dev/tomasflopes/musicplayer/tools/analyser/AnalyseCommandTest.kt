package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.ExportHeader
import dev.tomasflopes.musicplayer.core.analysis.HandBuiltProfilerV1
import dev.tomasflopes.musicplayer.core.analysis.PcmBuffer
import dev.tomasflopes.musicplayer.core.analysis.PcmChunkSink
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import dev.tomasflopes.musicplayer.core.analysis.PcmInfo
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import dev.tomasflopes.musicplayer.core.analysis.ProfileExportCodec
import dev.tomasflopes.musicplayer.core.analysis.ProfileStream
import dev.tomasflopes.musicplayer.core.analysis.SoundProfiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

class AnalyseCommandTest {
    @TempDir
    lateinit var dir: Path

    private class FakeSource : PcmSource {
        val decoded = AtomicInteger()

        override fun info(location: String) = PcmInfo(RATE, 1, SECONDS * 1_000L)

        override fun decodeAll(location: String): PcmBuffer {
            if ("bad" in location) throw PcmDecodeException("cannot decode")
            decoded.incrementAndGet()
            val hz = 200.0 + location.length
            return PcmBuffer(
                RATE,
                1,
                0L,
                FloatArray(RATE * SECONDS) { (0.4 * sin(2.0 * PI * hz * it / RATE)).toFloat() },
            )
        }

        override fun decodeWindow(
            location: String,
            startMs: Long,
            lengthMs: Long,
        ): PcmBuffer = decodeAll(location)

        override fun decodeStream(
            location: String,
            sink: PcmChunkSink,
        ) {
            val pcm = decodeAll(location)
            sink.begin(pcm.sampleRate, pcm.channels)
            sink.accept(pcm.samples, pcm.samples.size)
        }
    }

    private class OtherVersion : SoundProfiler {
        override val version = 99

        override fun newStream(sampleRate: Int): ProfileStream = HandBuiltProfilerV1().newStream(sampleRate)
    }

    private val root: Path get() = dir.resolve("music")
    private val out: Path get() = dir.resolve("profiles.jsonl")

    private fun song(
        name: String,
        bytes: Int = 10,
    ) {
        val file = root.resolve(name)
        Files.createDirectories(file.parent)
        Files.write(file, ByteArray(bytes))
    }

    private fun run(
        source: PcmSource = FakeSource(),
        profiler: SoundProfiler = HandBuiltProfilerV1(),
    ): Pair<Int, String> {
        val text = ByteArrayOutputStream()
        val code = AnalyseCommand(source, profiler, PrintStream(text)).run(AnalyseOptions(root, out, threads = 3))
        return code to text.toString()
    }

    private fun lines() = Files.readAllLines(out).filter { it.isNotBlank() }

    @Test
    fun `writes a header and one line per song`() {
        song("a.mp3")
        song("Sub/é.mp3")

        val (code, _) = run()

        assertEquals(0, code)
        val read = ProfileExportCodec.read(lines())
        assertEquals(HandBuiltProfilerV1.VERSION, read.header.profilerVersion)
        assertEquals("music", read.header.musicRootHint)
        assertEquals(listOf("Sub/é.mp3", "a.mp3"), read.records.map { it.path }.sorted())
        assertEquals(setOf(10L), read.records.map { it.size }.toSet())
        assertEquals(SECONDS * 1_000L, read.records.first().durationMs)
        assertTrue(read.records.all { it.profile.meanLoudness > 0f })
    }

    @Test
    fun `a rerun skips the songs that are done and picks up new and changed ones`() {
        song("a.mp3")
        song("b.mp3")
        run()
        song("c.mp3")
        song("b.mp3", bytes = 20)
        val source = FakeSource()

        val (_, text) = run(source)

        assertEquals(2, source.decoded.get())
        assertTrue(text.contains("1 already done, 2 to analyse"), text)
        assertEquals(
            setOf("a.mp3", "b.mp3", "c.mp3"),
            ProfileExportCodec
                .read(lines())
                .records
                .map { it.path }
                .toSet(),
        )
    }

    @Test
    fun `a half written last line is cut off and its song is done again`() {
        song("a.mp3")
        song("b.mp3")
        run()
        val all = Files.readAllLines(out)
        Files.writeString(out, (all.dropLast(1) + all.last().dropLast(30)).joinToString("\n"))
        val source = FakeSource()

        run(source)

        assertEquals(1, source.decoded.get())
        val read = ProfileExportCodec.read(lines())
        assertEquals(2, read.records.size)
        assertEquals(false, read.truncatedTail)
    }

    @Test
    fun `another profiler version stops the run and leaves the file alone`() {
        song("a.mp3")
        run()
        val before = Files.readString(out)

        val error = assertThrows(UsageException::class.java) { run(profiler = OtherVersion()) }

        assertTrue(error.message!!.contains("version 1"), error.message)
        assertEquals(before, Files.readString(out))
    }

    @Test
    fun `a song that cannot be decoded is listed and left out so a rerun retries it`() {
        song("good.mp3")
        song("bad.mp3")

        val (code, text) = run()

        assertEquals(0, code)
        assertTrue(text.contains("failed: bad.mp3: cannot decode"), text)
        assertEquals(listOf("good.mp3"), ProfileExportCodec.read(lines()).records.map { it.path })
        val retry = FakeSource()
        val (_, again) = run(retry)
        assertTrue(again.contains("1 already done, 1 to analyse"), again)
    }

    @Test
    fun `the header names the profiler version and the music folder`() {
        song("a.mp3")
        run()

        assertEquals(ProfileExportCodec.headerLine(ExportHeader(1, "music")), lines().first())
    }

    @Test
    fun `the analyse options need a root and an out`() {
        assertEquals(3, AnalyseOptions.parse(listOf("--root", "r", "--out", "o", "--threads", "3")).threads)
        assertThrows(UsageException::class.java) { AnalyseOptions.parse(listOf("--root", "r")) }
        assertThrows(
            UsageException::class.java,
        ) { AnalyseOptions.parse(listOf("--root", "r", "--out", "o", "--threads", "0")) }
        assertThrows(
            UsageException::class.java,
        ) { AnalyseOptions.parse(listOf("--root", "r", "--out", "o", "--what", "1")) }
    }

    private companion object {
        const val RATE = 22_050
        const val SECONDS = 4
    }
}
