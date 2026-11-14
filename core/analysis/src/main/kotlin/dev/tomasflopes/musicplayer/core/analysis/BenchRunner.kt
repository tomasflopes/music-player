package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.core.analysis.config.SpectrumConfig

/** What one song cost, stage by stage (ADR 0054): (a) full decode, (b) seek-decode of the windows, (c) the spectrum. */
data class SongTimings(
    val sampleRate: Int,
    val fullDecodeNs: Long,
    val seekDecodeNs: Long,
    val spectrumNs: Map<SpectrumConfig, Long>,
) {
    val totalNs: Long get() = fullDecodeNs + seekDecodeNs + spectrumNs.values.sum()
}

/**
 * Times one song through the stages of the spike. Shared by `analyser bench` and the phone's "Analysis benchmark"
 * screen, so both measure the same thing; only the [PcmSource] differs.
 */
class BenchRunner(
    private val source: PcmSource,
    private val planner: WindowPlanner = WindowPlanner(),
    private val spectrum: List<SpectrumConfig> = SpectrumConfig.SPIKE_VARIANTS,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /** Throws [PcmDecodeException] when the file cannot be decoded. */
    fun measure(location: String): SongTimings {
        val fullStart = nanoTime()
        val full = source.decodeAll(location)
        val fullNs = nanoTime() - fullStart

        val seekNs = timeSeekDecode(location)

        val mono = MonoDownmix.toMono(full)
        val windows = planner.plan(full.durationMs).map { mono.window(it.startMs, it.lengthMs) }
        val spectrumNs = spectrum.associateWith { timeSpectrum(it, windows) }
        return SongTimings(full.sampleRate, fullNs, seekNs, spectrumNs)
    }

    /** Includes the [PcmSource.info] call, because seeking needs the duration to place the windows. */
    private fun timeSeekDecode(location: String): Long {
        val start = nanoTime()
        val info = source.info(location)
        for (window in planner.plan(info.durationMs)) {
            source.decodeWindow(location, window.startMs, window.lengthMs)
        }
        return nanoTime() - start
    }

    private fun timeSpectrum(
        config: SpectrumConfig,
        windows: List<FloatArray>,
    ): Long {
        val stub = SpectrumCostStub(config)
        val start = nanoTime()
        var checksum = 0.0
        for (window in windows) checksum += stub.run(window)
        val elapsed = nanoTime() - start
        check(!checksum.isNaN()) { "Spectrum stub produced NaN" }
        return elapsed
    }
}
