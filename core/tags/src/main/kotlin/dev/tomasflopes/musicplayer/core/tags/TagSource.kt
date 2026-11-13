package dev.tomasflopes.musicplayer.core.tags

import java.io.InputStream

/** One audio file, as far as tag reading goes: a file on disk, a MediaStore descriptor, or test bytes. */
interface TagSource {
    /** Name only, no folder. Used in warnings and reports. */
    val fileName: String

    /** A new stream at the first byte of the file. The caller closes it. */
    fun openStream(): InputStream

    /** The last [byteCount] bytes of the file, or `null` when the file is shorter than that. */
    fun readTail(byteCount: Int): ByteArray?
}
