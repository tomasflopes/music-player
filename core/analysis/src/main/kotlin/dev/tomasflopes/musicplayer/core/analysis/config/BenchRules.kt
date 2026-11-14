package dev.tomasflopes.musicplayer.core.analysis.config

/** The three thresholds of ADR 0054, in one place so the Mac and the phone print the same verdicts. */
data class BenchRules(
    /** Full decode if the p95 of one full decode is at most this; otherwise seek. */
    val fullDecodeP95LimitMs: Long = 2_000L,
    /** No resampling if at least this share of the files is 44.1 kHz. */
    val minShare44100: Double = 0.98,
    /** FFT 1024 if the large spectrum framing costs more than this share of a full decode. */
    val maxSpectrumShareOfDecode: Double = 0.25,
    /** Library size the estimate is made for. */
    val librarySongs: Int = 16_000,
)
