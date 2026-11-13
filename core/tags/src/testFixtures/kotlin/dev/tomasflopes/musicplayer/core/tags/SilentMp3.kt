package dev.tomasflopes.musicplayer.core.tags

/** Generated silence, so the repo never contains or downloads real music. */
object SilentMp3 {
    private const val SAMPLE_RATE = 44_100
    private const val SAMPLES_PER_FRAME = 1_152
    private const val FRAME_BYTES = 104

    /** MPEG-1 Layer III, 32 kbit/s, 44.1 kHz, mono, no CRC. Then all-zero side info and data, which is silence. */
    private val FRAME_HEADER = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x10, 0xC0.toByte())

    /** Valid MP3 frames lasting about [seconds], with no tag. */
    fun frames(seconds: Int): ByteArray {
        val frameCount = (seconds * SAMPLE_RATE + SAMPLES_PER_FRAME - 1) / SAMPLES_PER_FRAME
        val frame = FRAME_HEADER.copyOf(FRAME_BYTES)
        return ByteArray(frameCount * FRAME_BYTES) { index -> frame[index % FRAME_BYTES] }
    }
}
