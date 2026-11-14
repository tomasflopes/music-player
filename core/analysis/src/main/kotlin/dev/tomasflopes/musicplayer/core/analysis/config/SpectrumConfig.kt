package dev.tomasflopes.musicplayer.core.analysis.config

/** One framing of the spectrum: [fftSize] samples per frame, a new frame every [hop] samples. */
data class SpectrumConfig(
    val fftSize: Int,
    val hop: Int,
) {
    val label: String get() = "FFT $fftSize/$hop"

    companion object {
        val LARGE = SpectrumConfig(fftSize = 2048, hop = 512)
        val SMALL = SpectrumConfig(fftSize = 1024, hop = 512)

        /** The two framings the timing spike compares (ADR 0054). */
        val SPIKE_VARIANTS = listOf(LARGE, SMALL)
    }
}
