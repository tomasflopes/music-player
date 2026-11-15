package dev.tomasflopes.musicplayer.core.analysis

/**
 * Where the PCM comes from (ADR 0053): `FfmpegPcmSource` on the Mac, `MediaCodecPcmSource` on the phone.
 *
 * A `location` is whatever the implementation opens: a file path on the Mac, a file path or `content://` URI on the
 * phone. Implementations return PCM at the file's own sample rate and channel count; downmix to mono and windowing
 * happen in this module, once, so both sides feed the same code.
 */
interface PcmSource {
    /** Format and length of the first audio stream, without decoding it. */
    fun info(location: String): PcmInfo

    /** The whole file. */
    fun decodeAll(location: String): PcmBuffer

    /**
     * About [lengthMs] of audio from about [startMs]. The buffer says where it really starts
     * ([PcmBuffer.startMs]); a seek can land a little early, and it can come back a little long.
     */
    fun decodeWindow(
        location: String,
        startMs: Long,
        lengthMs: Long,
    ): PcmBuffer

    /**
     * The whole file as a stream of interleaved float chunks, in order, without ever holding the song (ADR 0054: a
     * whole-song `FloatArray` does not fit the phone's default heap). [sink] gets `begin` once, before the first
     * chunk, then `accept` for each chunk. Throws [PcmDecodeException] when the file cannot be decoded or holds no
     * audio; chunks already delivered stay delivered.
     */
    fun decodeStream(
        location: String,
        sink: PcmChunkSink,
    )
}

/** Receives what [PcmSource.decodeStream] decodes. */
interface PcmChunkSink {
    /** The format of every chunk that follows. Called once, before the first [accept]. */
    fun begin(
        sampleRate: Int,
        channels: Int,
    )

    /**
     * The first [count] values of [samples], interleaved, a whole number of frames, in -1..1. At most
     * [PcmChunkSink.MAX_CHUNK_SAMPLES] long. The decoder reuses [samples] after this returns, so copy what you keep.
     */
    fun accept(
        samples: FloatArray,
        count: Int,
    )

    companion object {
        /** No chunk is longer than this (1 MiB of floats), so a stream's memory does not grow with the song. */
        const val MAX_CHUNK_SAMPLES = 1 shl 18
    }
}

/** The file could not be opened or decoded. [message] says why, in words fit for a failure list. */
class PcmDecodeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
