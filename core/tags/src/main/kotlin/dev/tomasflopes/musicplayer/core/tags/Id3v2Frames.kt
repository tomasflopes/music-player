package dev.tomasflopes.musicplayer.core.tags

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PushbackInputStream

private const val V2_2 = 2
private const val V2_3 = 3
private const val V2_4 = 4
private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8
private const val SYNCHSAFE_MAX = 0x7F
private const val NUL: Byte = 0
private val NOTHING = ByteArray(0)

/** One frame of an ID3v2 tag. Usable until the next call to [Id3v2Frames.next]. */
internal class Id3v2Frame(
    val id: String,
    /** `compressed` or `encrypted` when the payload cannot be used as it is stored, otherwise `null`. */
    val unreadableBecause: String?,
    val size: Int,
    /** v2.4 only: the size if the writer stored a plain 32-bit number, not a synchsafe one, when that differs. */
    val plainSize: Int?,
    val unsynchronised: Boolean,
    val prefixSize: Int,
) {
    /** The most bytes the payload can take up, whichever way the size was written. */
    val maxSize: Int get() = plainSize ?: size
}

/**
 * Walks the frames of one ID3v2 tag, front to back, over a stream that ends where the tag body ends.
 * A payload is only loaded when [payload] is called; otherwise [next] skips it.
 */
internal class Id3v2Frames(
    private val header: Id3v2Header,
    tagBody: InputStream,
    private val warnings: MutableList<String>,
) {
    private val layout = FrameHeaderLayout(header)
    private val input =
        FrameInput(if (header.unsynchronised && header.major < V2_4) UnsynchronisedInputStream(tagBody) else tagBody)
    private var extendedHeaderPending = header.hasExtendedHeader
    private var unconsumed: Id3v2Frame? = null
    private var finished = false

    /** The next frame, or `null` at the padding, at the end of the tag, or where the tag stops making sense. */
    fun next(): Id3v2Frame? {
        val frame = if (!finished && moveToFrameStart()) readFrameHeader() else null
        finished = frame == null
        unconsumed = frame
        return frame
    }

    /**
     * The frame's content, without its grouping byte and data-length prefix and with unsynchronisation
     * undone. `null` when the tag ends before the frame does. Check [Id3v2Frame.maxSize] first: this allocates it.
     */
    fun payload(frame: Id3v2Frame): ByteArray? {
        val stored = consume(frame, keep = true)
        val content = if (stored != null && frame.unsynchronised) resynchronise(stored) else stored
        return content?.let { it.copyOfRange(minOf(frame.prefixSize, it.size), it.size) }
    }

    private fun moveToFrameStart(): Boolean {
        val previous = unconsumed
        val skipExtendedHeader = extendedHeaderPending
        extendedHeaderPending = false
        return when {
            skipExtendedHeader -> skipExtendedHeader()
            previous != null -> consume(previous, keep = false) != null
            else -> true
        }
    }

    private fun skipExtendedHeader(): Boolean {
        val toSkip = input.read(FrameHeaderLayout.EXTENDED_HEADER_SIZE_FIELD)?.let(layout::extendedHeaderRest) ?: -1L
        val skipped = toSkip >= 0 && input.skip(toSkip)
        if (!skipped) warnings += "stopped at an unreadable extended header"
        return skipped
    }

    private fun readFrameHeader(): Id3v2Frame? {
        val head = input.read(layout.headerSize)?.takeIf { it[0] != NUL }
        val frame = head?.let(layout::frame)
        return when {
            head == null -> null
            !layout.startsWithFrameId(head) -> null.also { warnings += "stopped at an unreadable frame id" }
            frame == null -> null.also { warnings += tooLarge(layout.id(head)) }
            else -> frame
        }
    }

    /**
     * Reads or skips the stored payload. For a v2.4 frame whose size could be either synchsafe or plain, the
     * synchsafe reading is tried first; if what follows it is neither a frame, padding nor the end of the
     * tag, the writer used plain sizes (old iTunes) and the frame runs on to the plain size.
     */
    private fun consume(
        frame: Id3v2Frame,
        keep: Boolean,
    ): ByteArray? {
        unconsumed = null
        val start = input.take(frame.size, keep)
        val rest =
            when {
                start == null -> null
                frame.plainSize == null || atFrameBoundary() -> NOTHING
                else -> input.take(frame.plainSize - frame.size, keep)
            }
        if (start == null || rest == null) {
            warnings += tooLarge(frame.id)
            finished = true
        }
        return if (start == null || rest == null) null else start + rest
    }

    private fun atFrameBoundary(): Boolean {
        val peeked = input.peek(layout.headerSize)
        return when {
            peeked.isEmpty() -> true
            peeked[0] == NUL -> peeked.all { it == NUL }
            peeked.size < layout.headerSize -> false
            else -> layout.startsWithFrameId(peeked) && layout.frame(peeked) != null
        }
    }

    private fun tooLarge(id: String) = "stopped at $id: frame is larger than the tag"

    private fun resynchronise(stored: ByteArray): ByteArray =
        UnsynchronisedInputStream(ByteArrayInputStream(stored)).readBytes()
}

/** Which bit of a frame's format-flags byte means what. The layout changed between v2.3 and v2.4. */
private class FrameFlagBits(
    val compressed: Int = 0,
    val encrypted: Int = 0,
    val grouped: Int = 0,
    val unsynchronised: Int = 0,
    val dataLengthIndicator: Int = 0,
)

/** How frame headers (and the extended header) are laid out in the tag's version, and how to read one. */
private class FrameHeaderLayout(
    private val header: Id3v2Header,
) {
    private val idSize = if (header.major == V2_2) V2_2_ID_SIZE else ID_SIZE
    private val flagBits =
        when (header.major) {
            V2_2 -> FrameFlagBits()
            V2_3 -> FrameFlagBits(compressed = 0x80, encrypted = 0x40, grouped = 0x20)
            else ->
                FrameFlagBits(
                    grouped = 0x40,
                    compressed = 0x08,
                    encrypted = 0x04,
                    unsynchronised = 0x02,
                    dataLengthIndicator = 0x01,
                )
        }

    val headerSize = if (header.major == V2_2) V2_2_FRAME_HEADER_SIZE else FRAME_HEADER_SIZE

    fun id(frameHeader: ByteArray) = String(frameHeader, 0, idSize, Charsets.ISO_8859_1)

    fun startsWithFrameId(frameHeader: ByteArray): Boolean = id(frameHeader).all { it in 'A'..'Z' || it in '0'..'9' }

    /** `null` when the frame claims to be larger than the whole tag. */
    fun frame(frameHeader: ByteArray): Id3v2Frame? {
        // The size field is as long as the id: three bytes in v2.2, four after that.
        val sizeBytes = (idSize until 2 * idSize).map { frameHeader[it].toInt() and BYTE_MASK }
        val plainSize = plainNumber(sizeBytes)
        // Only v2.4 stores frame sizes synchsafe, and a byte above 7F proves a writer did not.
        val synchsafe = header.major == V2_4 && sizeBytes.all { it <= SYNCHSAFE_MAX }
        val size = if (synchsafe) synchsafe(*sizeBytes.toIntArray()).toLong() else plainSize
        val flags = if (header.major == V2_2) 0 else frameHeader[FORMAT_FLAGS_INDEX].toInt() and BYTE_MASK
        val unsynchronisedFrame = header.unsynchronised || flags and flagBits.unsynchronised != 0
        return Id3v2Frame(
            id = id(frameHeader),
            unreadableBecause = unreadableBecause(flags),
            size = size.toInt(),
            plainSize = plainSize.takeIf { it != size && it <= header.tagSize }?.toInt(),
            unsynchronised = header.major == V2_4 && unsynchronisedFrame,
            prefixSize = prefixSize(flags),
        ).takeIf { size <= header.tagSize }
    }

    /** How many bytes of the extended header follow its four size bytes. Negative when the size is nonsense. */
    fun extendedHeaderRest(sizeField: ByteArray): Long {
        val sizeBytes = sizeField.map { it.toInt() and BYTE_MASK }
        // v2.4 counts the size field itself and stores the number synchsafe; v2.3 does neither.
        return if (header.major == V2_4) {
            synchsafe(*sizeBytes.toIntArray()).toLong() - EXTENDED_HEADER_SIZE_FIELD
        } else {
            plainNumber(sizeBytes)
        }
    }

    private fun unreadableBecause(flags: Int): String? =
        when {
            flags and flagBits.compressed != 0 -> "compressed"
            flags and flagBits.encrypted != 0 -> "encrypted"
            else -> null
        }

    private fun prefixSize(flags: Int): Int {
        val groupingByte = if (flags and flagBits.grouped != 0) 1 else 0
        val dataLength = if (flags and flagBits.dataLengthIndicator != 0) DATA_LENGTH_INDICATOR_SIZE else 0
        return groupingByte + dataLength
    }

    private fun plainNumber(bytes: List<Int>): Long =
        bytes.fold(0L) { value, byte -> (value shl BYTE_BITS) or byte.toLong() }

    companion object {
        const val EXTENDED_HEADER_SIZE_FIELD = 4
        private const val ID_SIZE = 4
        private const val FRAME_HEADER_SIZE = 10
        private const val V2_2_ID_SIZE = 3
        private const val V2_2_FRAME_HEADER_SIZE = 6
        private const val FORMAT_FLAGS_INDEX = 9
        private const val DATA_LENGTH_INDICATOR_SIZE = 4
    }
}

/** All-or-nothing reads and skips over the tag body, plus a look at the bytes that come next. */
private class FrameInput(
    tagBody: InputStream,
) {
    private val stream = PushbackInputStream(tagBody, MAX_PEEK)

    /** [count] bytes, or `null` when the tag body ends first. */
    fun read(count: Int): ByteArray? = ByteArray(count).takeIf { stream.readNBytes(it, 0, count) == count }

    /** `false` when the tag body ends first. */
    fun skip(count: Long): Boolean {
        var left = count
        var ended = false
        while (left > 0 && !ended) {
            val skipped = stream.skip(left)
            when {
                skipped > 0 -> left -= skipped
                stream.read() >= 0 -> left--
                else -> ended = true
            }
        }
        return !ended
    }

    /** Reads when [keep] is set, otherwise skips and returns an empty array. `null` when the tag body ends first. */
    fun take(
        count: Int,
        keep: Boolean,
    ): ByteArray? =
        when {
            keep -> read(count)
            skip(count.toLong()) -> NOTHING
            else -> null
        }

    /** Up to [count] of the next bytes (at most [MAX_PEEK]), without consuming them. */
    fun peek(count: Int): ByteArray {
        val buffer = ByteArray(minOf(count, MAX_PEEK))
        val peeked = buffer.copyOf(stream.readNBytes(buffer, 0, buffer.size))
        stream.unread(peeked)
        return peeked
    }

    private companion object {
        const val MAX_PEEK = 10
    }
}
