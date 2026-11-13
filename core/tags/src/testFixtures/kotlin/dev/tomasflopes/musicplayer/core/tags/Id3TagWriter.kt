package dev.tomasflopes.musicplayer.core.tags

import java.io.ByteArrayOutputStream

/** Text encodings of ID3v2 text frames; [byte] is the encoding byte that starts the frame. */
enum class TextEncoding(
    val byte: Int,
) {
    LATIN1(0),
    UTF16(1),
    UTF16BE(2),
    UTF8(3),
}

/** `id3v22 { text("TT2", "Song") }` gives the bytes of an ID3v2.2 tag (plus any `audio(...)` after it). */
fun id3v22(build: Id3TagWriter.() -> Unit): ByteArray = Id3TagWriter(Id3TagWriter.V2_2).apply(build).toByteArray()

fun id3v23(build: Id3TagWriter.() -> Unit): ByteArray = Id3TagWriter(Id3TagWriter.V2_3).apply(build).toByteArray()

fun id3v24(build: Id3TagWriter.() -> Unit): ByteArray = Id3TagWriter(Id3TagWriter.V2_4).apply(build).toByteArray()

/** A 128-byte ID3v1 tag (v1.1 when [track] is given). Text longer than 30 bytes is cut, as the format requires. */
fun id3v1(
    title: String = "",
    artist: String = "",
    album: String = "",
    track: Int? = null,
): ByteArray {
    val comment = if (track == null) ByteArray(V1_FIELD) else ByteArray(V1_FIELD - 1) + track.toByte()
    val year = ByteArray(V1_YEAR)
    val genre = byteArrayOf(V1_NO_GENRE)
    return "TAG".latin1() + v1Field(title) + v1Field(artist) + v1Field(album) + year + comment + genre
}

private const val V1_FIELD = 30
private const val V1_YEAR = 4
private const val V1_NO_GENRE: Byte = -1

private fun v1Field(text: String): ByteArray = text.latin1().copyOf(V1_FIELD)

private fun String.latin1(): ByteArray = toByteArray(Charsets.ISO_8859_1)

/**
 * Writes ID3v2 tags byte by byte for tests, including the odd shapes real tools produce. It shares no code
 * with the reader on purpose: a bug copied into both would make the tests pass for the wrong reason.
 */
class Id3TagWriter internal constructor(
    private val major: Int,
) {
    private class Frame(
        val id: String,
        val payload: ByteArray,
        val flags: Int,
    )

    private val frames = mutableListOf<Frame>()
    private var unsynchronised = false
    private var extendedHeader = false
    private var plainFrameSizes = false
    private var padding = 0
    private var declaredTagSize: Int? = null
    private var audio = ByteArray(0)

    fun text(
        id: String,
        value: String,
        encoding: TextEncoding = TextEncoding.LATIN1,
    ) {
        rawFrame(id, byteArrayOf(encoding.byte.toByte()) + encode(value, encoding))
    }

    /** `APIC` (or `PIC` in v2.2) with an empty description. */
    fun picture(
        data: ByteArray,
        type: Int = FRONT_COVER,
        mimeType: String = "image/png",
    ) {
        val latin1Text: Byte = 0
        if (major == V2_2) {
            rawFrame("PIC", byteArrayOf(latin1Text) + "PNG".latin1() + type.toByte() + NUL + data)
        } else {
            rawFrame("APIC", byteArrayOf(latin1Text) + mimeType.latin1() + NUL + type.toByte() + NUL + data)
        }
    }

    /** Any frame. [flags] are the two flag bytes of a v2.3/v2.4 frame header as one number (ignored in v2.2). */
    fun rawFrame(
        id: String,
        payload: ByteArray,
        flags: Int = 0,
    ) {
        frames += Frame(id, payload, flags)
    }

    /** v2.2/v2.3: the whole tag is unsynchronised. v2.4: every frame is, and says so in its own flags. */
    fun unsynchronised() {
        unsynchronised = true
    }

    fun extendedHeader() {
        extendedHeader = true
    }

    fun padding(byteCount: Int) {
        padding = byteCount
    }

    /** v2.4 only: frame sizes as plain 32-bit numbers instead of synchsafe, the way old iTunes wrote them. */
    fun plainFrameSizes() {
        plainFrameSizes = true
    }

    /** Lies about the tag size in the header. */
    fun declaredTagSize(size: Int) {
        declaredTagSize = size
    }

    /** Bytes that follow the tag (the audio). */
    fun audio(bytes: ByteArray) {
        audio = bytes
    }

    internal fun toByteArray(): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(extendedHeaderBytes())
        frames.forEach { body.write(frameBytes(it)) }
        body.write(ByteArray(padding))
        val stored = if (unsynchronised && major < V2_4) unsynchronise(body.toByteArray()) else body.toByteArray()
        val unsynchronisedFlag = if (unsynchronised) FLAG_UNSYNCHRONISED else 0
        val extendedHeaderFlag = if (extendedHeader) FLAG_EXTENDED_HEADER else 0
        val flags = (unsynchronisedFlag or extendedHeaderFlag).toByte()
        val size = synchsafe(declaredTagSize ?: stored.size)
        return "ID3".latin1() + byteArrayOf(major.toByte(), 0, flags) + size + stored + audio
    }

    private fun frameBytes(frame: Frame): ByteArray {
        val unsynchroniseFrame = unsynchronised && major == V2_4
        val payload = if (unsynchroniseFrame) unsynchronise(frame.payload) else frame.payload
        val flags = frame.flags or (if (unsynchroniseFrame) FRAME_FLAG_UNSYNCHRONISED else 0)
        val flagBytes = byteArrayOf((flags shr BYTE_BITS).toByte(), flags.toByte())
        return when {
            major == V2_2 -> frame.id.latin1() + plain(payload.size).copyOfRange(1, INT_BYTES) + payload
            major == V2_4 && !plainFrameSizes -> frame.id.latin1() + synchsafe(payload.size) + flagBytes + payload
            else -> frame.id.latin1() + plain(payload.size) + flagBytes + payload
        }
    }

    private fun extendedHeaderBytes(): ByteArray =
        when {
            !extendedHeader -> ByteArray(0)
            major == V2_4 -> synchsafe(V2_4_EXTENDED_HEADER_SIZE) + byteArrayOf(1, 0)
            else -> plain(V2_3_EXTENDED_HEADER_SIZE) + ByteArray(V2_3_EXTENDED_HEADER_SIZE)
        }

    private fun encode(
        value: String,
        encoding: TextEncoding,
    ): ByteArray =
        when (encoding) {
            TextEncoding.LATIN1 -> value.latin1()
            TextEncoding.UTF16 -> BOM_LITTLE_ENDIAN + value.toByteArray(Charsets.UTF_16LE)
            TextEncoding.UTF16BE -> value.toByteArray(Charsets.UTF_16BE)
            TextEncoding.UTF8 -> value.toByteArray(Charsets.UTF_8)
        }

    /** A zero byte after every FF: more than the standard asks for, and still undone by the standard's rule. */
    private fun unsynchronise(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size)
        bytes.forEach { byte ->
            out.write(byte.toInt())
            if (byte == FF) out.write(NUL.toInt())
        }
        return out.toByteArray()
    }

    private fun synchsafe(value: Int): ByteArray =
        ByteArray(INT_BYTES) { index ->
            ((value shr (SYNCHSAFE_BITS * (INT_BYTES - 1 - index))) and SYNCHSAFE_MASK).toByte()
        }

    private fun plain(value: Int): ByteArray =
        ByteArray(INT_BYTES) { index -> (value shr (BYTE_BITS * (INT_BYTES - 1 - index))).toByte() }

    companion object {
        const val FRONT_COVER = 3
        const val OTHER_PICTURE = 0
        internal const val V2_2 = 2
        internal const val V2_3 = 3
        internal const val V2_4 = 4
        private const val NUL: Byte = 0
        private const val FF: Byte = -1
        private const val INT_BYTES = 4
        private const val BYTE_BITS = 8
        private const val SYNCHSAFE_BITS = 7
        private const val SYNCHSAFE_MASK = 0x7F
        private const val FLAG_UNSYNCHRONISED = 0x80
        private const val FLAG_EXTENDED_HEADER = 0x40
        private const val FRAME_FLAG_UNSYNCHRONISED = 0x02
        private const val V2_3_EXTENDED_HEADER_SIZE = 6
        private const val V2_4_EXTENDED_HEADER_SIZE = 6
        private val BOM_LITTLE_ENDIAN = byteArrayOf(-1, -2)
    }
}
