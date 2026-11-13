package dev.tomasflopes.musicplayer.core.tags

import java.io.ByteArrayInputStream
import java.io.InputStream

/** A file held in memory. */
class ByteArrayTagSource(
    private val bytes: ByteArray,
    override val fileName: String = "test.mp3",
) : TagSource {
    override fun openStream(): InputStream = ByteArrayInputStream(bytes)

    override fun readTail(byteCount: Int): ByteArray? =
        if (bytes.size < byteCount) null else bytes.copyOfRange(bytes.size - byteCount, bytes.size)
}
