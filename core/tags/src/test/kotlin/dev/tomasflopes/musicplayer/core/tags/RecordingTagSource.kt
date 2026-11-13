package dev.tomasflopes.musicplayer.core.tags

import java.io.ByteArrayInputStream
import java.io.InputStream

/** Counts what a reader pulls into memory, as opposed to what it skips over. */
internal class RecordingTagSource(
    private val bytes: ByteArray,
) : TagSource {
    var bytesRead = 0L
        private set
    var largestReadRequest = 0
        private set

    override val fileName = "recorded.mp3"

    override fun openStream(): InputStream =
        object : ByteArrayInputStream(bytes) {
            override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }

            override fun read(
                buffer: ByteArray,
                offset: Int,
                length: Int,
            ): Int {
                largestReadRequest = maxOf(largestReadRequest, length)
                return super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
            }
        }

    override fun readTail(byteCount: Int): ByteArray? = null
}
