package dev.tomasflopes.musicplayer.core.analysis

private const val MS_PER_SECOND = 1_000L

/** What a decoder knows about a file without decoding it. */
data class PcmInfo(
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long,
)

/**
 * Decoded audio: [samples] are interleaved float PCM in -1..1 at the decoder's own [sampleRate] (no resampling).
 * [startMs] is where the first frame sits in the song: 0 after a full decode, near the request after a seek.
 */
class PcmBuffer(
    val sampleRate: Int,
    val channels: Int,
    val startMs: Long,
    val samples: FloatArray,
) {
    val frames: Int get() = samples.size / channels
    val durationMs: Long get() = frames * MS_PER_SECOND / sampleRate
}
