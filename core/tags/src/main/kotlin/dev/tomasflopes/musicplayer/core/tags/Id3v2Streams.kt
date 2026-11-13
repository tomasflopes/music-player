package dev.tomasflopes.musicplayer.core.tags

import java.io.InputStream

private const val FF = 0xFF

/**
 * The body of an ID3v2 tag: reads stop where the tag ends, and [truncated] says whether the file ended first.
 * Skipping never loads the skipped bytes, which is what keeps multi-megabyte cover art out of memory.
 */
internal class TagBodyStream(
    private val file: InputStream,
    size: Int,
) : InputStream() {
    private var remaining = size.toLong()

    var truncated = false
        private set

    override fun read(): Int {
        val byte = if (remaining > 0) file.read() else -1
        if (byte >= 0) {
            remaining--
        } else if (remaining > 0) {
            truncated = true
        }
        return byte
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val wanted = minOf(length.toLong(), remaining).toInt()
        val count =
            if (wanted > 0) {
                file.read(buffer, offset, wanted)
            } else if (length == 0) {
                0
            } else {
                -1
            }
        if (count > 0) remaining -= count
        if (wanted > 0 && count < 0) truncated = true
        return count
    }

    override fun skip(count: Long): Long {
        val wanted = minOf(count, remaining)
        var skipped = 0L
        while (skipped < wanted && !truncated) {
            skipped += skipSome(wanted - skipped)
        }
        remaining -= skipped
        return skipped
    }

    fun skipToEnd() {
        skip(remaining)
    }

    /**
     * Seeks over all but the last byte and then reads that one: a file stream happily seeks past the end of
     * its file, so only a read proves the bytes exist.
     */
    private fun skipSome(count: Long): Long {
        val seeked = if (count > 1) file.skip(count - 1) else 0L
        val advanced =
            if (seeked > 0) {
                seeked
            } else if (file.read() >= 0) {
                1L
            } else {
                0L
            }
        if (advanced == 0L) truncated = true
        return advanced
    }
}

/** Undoes ID3v2 unsynchronisation while reading: the zero byte the writer put after every FF is dropped. */
internal class UnsynchronisedInputStream(
    private val stored: InputStream,
) : InputStream() {
    private var afterFf = false

    override fun read(): Int {
        var byte = stored.read()
        if (afterFf && byte == 0) byte = stored.read()
        afterFf = byte == FF
        return byte
    }
}
