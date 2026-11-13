package dev.tomasflopes.musicplayer.core.tags

private const val BYTE_MASK = 0xFF
private const val SYNCHSAFE_MAX = 0x7F
private const val SYNCHSAFE_BITS = 7

/** The number ID3v2 stores as bytes of 7 bits each, most significant first (28 bits in four bytes). */
internal fun synchsafe(vararg bytes: Int): Int = bytes.fold(0) { value, byte -> (value shl SYNCHSAFE_BITS) or byte }

/** The 10 bytes every ID3v2 tag starts with. [tagSize] excludes these 10 bytes (and the footer, if any). */
internal data class Id3v2Header(
    val major: Int,
    val unsynchronised: Boolean,
    val hasExtendedHeader: Boolean,
    val hasFooter: Boolean,
    val tagSize: Int,
) {
    companion object {
        const val SIZE = 10
        private const val MAJOR_INDEX = 3
        private const val FLAGS_INDEX = 5
        private const val SIZE_INDEX = 6
        private const val V2_2 = 2
        private const val V2_3 = 3
        private const val V2_4 = 4
        private const val FLAG_UNSYNCHRONISED = 0x80
        private const val FLAG_EXTENDED_HEADER = 0x40
        private const val FLAG_FOOTER = 0x10
        private val MAGIC = "ID3".toByteArray(Charsets.ISO_8859_1)

        /** `null` when [bytes] is not the start of an ID3v2.2, 2.3 or 2.4 tag. */
        fun parse(bytes: ByteArray): Id3v2Header? {
            if (bytes.size < SIZE || !MAGIC.indices.all { bytes[it] == MAGIC[it] }) return null
            val major = bytes[MAJOR_INDEX].toInt() and BYTE_MASK
            val flags = bytes[FLAGS_INDEX].toInt() and BYTE_MASK
            val size = (SIZE_INDEX until SIZE).map { bytes[it].toInt() and BYTE_MASK }
            val header =
                Id3v2Header(
                    major = major,
                    unsynchronised = flags and FLAG_UNSYNCHRONISED != 0,
                    hasExtendedHeader = major >= V2_3 && flags and FLAG_EXTENDED_HEADER != 0,
                    hasFooter = major == V2_4 && flags and FLAG_FOOTER != 0,
                    tagSize = synchsafe(*size.toIntArray()),
                )
            return header.takeIf { major in V2_2..V2_4 && size.all { it <= SYNCHSAFE_MAX } }
        }
    }
}
