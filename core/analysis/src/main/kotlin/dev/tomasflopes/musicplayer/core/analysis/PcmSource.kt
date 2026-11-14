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
}

/** The file could not be opened or decoded. [message] says why, in words fit for a failure list. */
class PcmDecodeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
