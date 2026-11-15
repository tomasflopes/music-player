package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.ExportRecord
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import dev.tomasflopes.musicplayer.core.analysis.SoundProfiler
import dev.tomasflopes.musicplayer.core.analysis.analyse
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private const val PROGRESS_EVERY = 100
private const val SECONDS_PER_MINUTE = 60L
private const val NANOS_PER_SECOND = 1_000_000_000L

/**
 * `analyser analyse`: profiles every MP3 under the music folder, in parallel, into one JSON Lines file (ADR 0003,
 * 0065). Rerunning resumes: songs already in the file with the same size are skipped, so Ctrl-C costs nothing.
 */
class AnalyseCommand(
    private val source: PcmSource,
    private val profiler: SoundProfiler,
    private val out: PrintStream,
) {
    fun run(options: AnalyseOptions): Int {
        val files = Mp3Finder.find(options.root)
        if (files.isEmpty()) return fail("No .mp3 files under ${options.root}")
        val hint =
            options.root
                .toAbsolutePath()
                .normalize()
                .fileName
                ?.toString()
                .orEmpty()
        ProfileFile(options.out, profiler.version, hint).use { file ->
            val done = file.open()
            val todo = files.entries.sortedBy { it.key }.filter { done[it.key] != Files.size(it.value) }
            out.println("Found ${files.size} files, ${files.size - todo.size} already done, ${todo.size} to analyse")
            val failures = analyseAll(todo.map { it.key to it.value }, file, options.threads)
            report(todo.size, failures)
            return if (todo.isNotEmpty() && failures.size == todo.size) 1 else 0
        }
    }

    private fun analyseAll(
        todo: List<Pair<String, Path>>,
        file: ProfileFile,
        threads: Int,
    ): List<String> {
        val failures = ConcurrentLinkedQueue<String>()
        val finished = AtomicInteger()
        val started = System.nanoTime()
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val tasks =
                todo.map { (key, path) ->
                    Callable {
                        analyseOne(key, path, file)?.let { failures += it }
                        val count = finished.incrementAndGet()
                        if (count % PROGRESS_EVERY == 0) progress(count, todo.size, failures.size, started)
                    }
                }
            pool.invokeAll(tasks).forEach { it.get() }
        } finally {
            pool.shutdown()
        }
        return failures.toList()
    }

    /** Null when the song is in the file, otherwise the line for the failure list. */
    private fun analyseOne(
        key: String,
        path: Path,
        file: ProfileFile,
    ): String? =
        try {
            val pcm = source.decodeAll(path.toString())
            file.append(ExportRecord(key, Files.size(path), pcm.durationMs, profiler.analyse(pcm)))
            null
        } catch (e: PcmDecodeException) {
            "$key: ${e.message}"
        }

    private fun progress(
        count: Int,
        total: Int,
        failed: Int,
        started: Long,
    ) {
        val seconds = (System.nanoTime() - started) / NANOS_PER_SECOND
        val elapsed = "${seconds / SECONDS_PER_MINUTE} min ${seconds % SECONDS_PER_MINUTE} s"
        out.println("Analysed $count/$total ($failed failed), $elapsed")
    }

    private fun report(
        total: Int,
        failures: List<String>,
    ) {
        out.println("Analysed ${total - failures.size} songs, ${failures.size} failed")
        failures.sorted().forEach { out.println("  failed: $it") }
    }

    private fun fail(message: String): Int {
        System.err.println(message)
        return 1
    }
}
