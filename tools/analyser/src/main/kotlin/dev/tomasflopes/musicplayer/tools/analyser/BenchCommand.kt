package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.BenchInput
import dev.tomasflopes.musicplayer.core.analysis.BenchReport
import dev.tomasflopes.musicplayer.core.analysis.BenchRun
import dev.tomasflopes.musicplayer.core.analysis.BenchRunner
import dev.tomasflopes.musicplayer.core.analysis.BenchSample
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import dev.tomasflopes.musicplayer.core.analysis.SampleRateCount
import dev.tomasflopes.musicplayer.core.analysis.SongTimings
import java.io.PrintStream
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * `analyser bench`: the timing spike on the Mac (ADR 0054). Times the same fixed-seed sample as the phone screen,
 * once on one thread and once on every core, and counts the sample rates over a larger sample.
 */
class BenchCommand(
    private val source: PcmSource,
    private val out: PrintStream,
) {
    private class Outcome(
        val timings: SongTimings?,
        val failure: String?,
    )

    fun run(options: BenchOptions): Int {
        val files = Mp3Finder.find(options.root)
        return if (files.isEmpty()) fail("No .mp3 files under ${options.root}") else bench(options, files)
    }

    private fun bench(
        options: BenchOptions,
        files: Map<String, Path>,
    ): Int {
        val sample = pick(files, options.sample, options.seed)
        val survey = pick(files, options.survey, options.seed)
        out.println("Found ${files.size} files; timing ${sample.size}, surveying ${survey.size}")
        val rates = SampleRateCount.of(survey.mapNotNull { sampleRateOf(it) })
        warmUp(sample)

        val runner = BenchRunner(source)
        val single = timed("1 thread", 1, sample, runner)
        val parallel = timed("${options.threads} threads", options.threads, sample, runner)
        val runs = listOf(single, parallel).filter { it.timings.isNotEmpty() }
        if (runs.isNotEmpty()) {
            val failed = sample.size - single.timings.size
            out.println()
            out.println(BenchReport.format(BenchInput(options.seed, failed, rates, runs)))
        }
        return if (runs.isEmpty()) fail("No song could be decoded.") else 0
    }

    private fun pick(
        files: Map<String, Path>,
        count: Int,
        seed: Long,
    ): List<String> = BenchSample.pick(files.keys, count, seed).map { files.getValue(it).toString() }

    private fun fail(message: String): Int {
        out.println(message)
        return 1
    }

    private fun sampleRateOf(location: String): Int? =
        try {
            source.info(location).sampleRate
        } catch (e: PcmDecodeException) {
            out.println("  survey skipped $location: ${e.message}")
            null
        }

    /** The first songs pay for JIT and the page cache; they are not counted. */
    private fun warmUp(sample: List<String>) {
        val runner = BenchRunner(source)
        sample.take(WARM_UP_SONGS).forEach { measure(runner, it) }
    }

    private fun timed(
        label: String,
        threads: Int,
        sample: List<String>,
        runner: BenchRunner,
    ): BenchRun {
        out.println("Running: $label")
        val pool = Executors.newFixedThreadPool(threads)
        val start = System.nanoTime()
        val outcomes =
            try {
                pool.invokeAll(sample.map { location -> Callable { measure(runner, location) } }).map { it.get() }
            } finally {
                pool.shutdown()
            }
        val wall = System.nanoTime() - start
        outcomes.mapNotNull { it.failure }.forEach { out.println("  failed: $it") }
        return BenchRun(label, threads, wall, outcomes.mapNotNull { it.timings })
    }

    private fun measure(
        runner: BenchRunner,
        location: String,
    ): Outcome =
        try {
            Outcome(runner.measure(location), null)
        } catch (e: PcmDecodeException) {
            Outcome(null, "$location: ${e.message}")
        }

    private companion object {
        const val WARM_UP_SONGS = 3
    }
}
