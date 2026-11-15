package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.core.analysis.config.BenchRules
import dev.tomasflopes.musicplayer.core.analysis.config.SpectrumConfig
import java.util.Locale

private const val NS_PER_MS = 1_000_000.0
private const val NS_PER_SECOND = 1_000_000_000.0
private const val PERCENT = 100.0
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/** How many of the surveyed files use each sample rate. */
class SampleRateCount(
    private val byRate: Map<Int, Int>,
) {
    val total: Int get() = byRate.values.sum()

    fun share(rate: Int): Double = if (total == 0) 0.0 else (byRate[rate] ?: 0).toDouble() / total

    fun describe(): String =
        byRate.entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key} Hz ${percent(it.value.toDouble() / total)}" }

    companion object {
        fun of(rates: List<Int>) = SampleRateCount(rates.groupingBy { it }.eachCount())
    }
}

/** One pass over the sample: [parallelism] songs at a time, [wallNs] from the first start to the last finish. */
class BenchRun(
    val label: String,
    val parallelism: Int,
    val wallNs: Long,
    val timings: List<SongTimings>,
)

class BenchInput(
    val seed: Long,
    val failed: Int,
    val sampleRates: SampleRateCount,
    val runs: List<BenchRun>,
    val rules: BenchRules = BenchRules(),
    /** How many of [failed] died with an out-of-memory error (the phone's whole-song decode, ADR 0054). */
    val failedByMemory: Int = 0,
)

/** The text both `analyser bench` and the phone screen print: median and p95 per stage, estimates and the rules. */
object BenchReport {
    fun format(input: BenchInput): String =
        buildString {
            val songs =
                input.runs
                    .firstOrNull()
                    ?.timings
                    ?.size ?: 0
            val failures = "${input.failed} failed, ${input.failedByMemory} by memory"
            appendLine("Analysis benchmark: $songs songs, seed ${input.seed}, $failures")
            appendLine("Sample rates over ${input.sampleRates.total} files: ${input.sampleRates.describe()}")
            input.runs.forEach { appendRun(it, input.rules) }
            input.runs.firstOrNull()?.let { appendRules(it, input) }
        }

    private fun StringBuilder.appendRun(
        run: BenchRun,
        rules: BenchRules,
    ) {
        val timings = run.timings
        appendLine()
        appendLine("${run.label} (wall ${seconds(run.wallNs.toDouble())})")
        appendLine(row("stage", "median", "p95"))
        appendLine(stage("full decode", timings.map { it.fullDecodeNs }))
        appendLine(stage("seek-decode, windows", timings.map { it.seekDecodeNs }))
        for (config in SpectrumConfig.SPIKE_VARIANTS) appendLine(stage(config.label, spectrumOf(timings, config)))
        appendLine("Estimate for ${"%,d".format(Locale.ROOT, rules.librarySongs)} songs:")
        appendLine("  full decode + ${SpectrumConfig.LARGE.label}: ${estimate(run, rules) { it.fullDecodeNs }}")
        appendLine("  seek-decode + ${SpectrumConfig.LARGE.label}: ${estimate(run, rules) { it.seekDecodeNs }}")
    }

    private fun StringBuilder.appendRules(
        run: BenchRun,
        input: BenchInput,
    ) {
        val rules = input.rules
        val timings = run.timings
        val decodeP95Ms = Stats.p95(timings.map { it.fullDecodeNs }) / NS_PER_MS
        val share = spectrumShare(timings)
        appendLine()
        appendLine("Rules of ADR 0054, from \"${run.label}\" (the phone's numbers decide; the Mac's are indicative)")
        appendLine(
            "  full decode p95 ${ms(decodeP95Ms)} vs limit ${rules.fullDecodeP95LimitMs} ms: " +
                if (decodeP95Ms <= rules.fullDecodeP95LimitMs) "full decode" else "seek",
        )
        val share44100 = input.sampleRates.share(SAMPLE_RATE_44100)
        appendLine(
            "  44.1 kHz share ${percent(share44100)} vs ${percent(rules.minShare44100)}: " +
                if (share44100 >= rules.minShare44100) "no resampling" else "resample to 22.05 kHz",
        )
        appendLine(
            "  ${SpectrumConfig.LARGE.label} is ${percent(share)} of decode " +
                "vs ${percent(rules.maxSpectrumShareOfDecode)}: " +
                if (share > rules.maxSpectrumShareOfDecode) "FFT 1024" else "FFT 2048",
        )
    }

    private fun spectrumOf(
        timings: List<SongTimings>,
        config: SpectrumConfig,
    ): List<Long> = timings.map { it.spectrumNs.getValue(config) }

    private fun spectrumShare(timings: List<SongTimings>): Double {
        val spectrum = Stats.median(spectrumOf(timings, SpectrumConfig.LARGE)).toDouble()
        return spectrum / Stats.median(timings.map { it.fullDecodeNs })
    }

    private fun estimate(
        run: BenchRun,
        rules: BenchRules,
        decodeStage: (SongTimings) -> Long,
    ): String {
        val perSongNs =
            Stats.median(run.timings.map(decodeStage)) +
                Stats.median(spectrumOf(run.timings, SpectrumConfig.LARGE))
        val busyNs = run.timings.sumOf { it.totalNs }.toDouble()
        val effectiveParallelism = if (run.wallNs == 0L) 1.0 else busyNs / run.wallNs
        val totalNs = perSongNs.toDouble() * rules.librarySongs / effectiveParallelism.coerceAtLeast(1.0)
        return "${duration(totalNs / NS_PER_SECOND)} on ${run.label}"
    }

    private fun stage(
        name: String,
        valuesNs: List<Long>,
    ): String = row(name, ms(Stats.median(valuesNs) / NS_PER_MS), ms(Stats.p95(valuesNs) / NS_PER_MS))

    private fun row(
        name: String,
        median: String,
        p95: String,
    ): String = "  %-22s %10s %10s".format(Locale.ROOT, name, median, p95)

    private fun ms(value: Double): String = "%.1f ms".format(Locale.ROOT, value)

    private fun seconds(ns: Double): String = "%.1f s".format(Locale.ROOT, ns / NS_PER_SECOND)

    private fun duration(totalSeconds: Double): String {
        val minutes = (totalSeconds / SECONDS_PER_MINUTE).toLong()
        return if (minutes >= MINUTES_PER_HOUR) {
            "${minutes / MINUTES_PER_HOUR} h ${minutes % MINUTES_PER_HOUR} min"
        } else {
            "$minutes min"
        }
    }

    private const val SAMPLE_RATE_44100 = 44_100
}

private fun percent(fraction: Double): String = "%.1f %%".format(Locale.ROOT, fraction * PERCENT)
