package dev.tomasflopes.musicplayer.core.tags

/** ID3v1 and v1.1: the fixed 128-byte block at the very end of a file. Only used when there is no ID3v2 tag. */
internal object Id3v1Reader {
    private const val TAG_SIZE = 128
    private const val FIELD_SIZE = 30
    private const val TITLE_START = 3
    private const val ARTIST_START = TITLE_START + FIELD_SIZE
    private const val ALBUM_START = ARTIST_START + FIELD_SIZE
    private const val TRACK_MARKER_INDEX = 125
    private const val TRACK_INDEX = 126
    private const val BYTE_MASK = 0xFF
    private const val NUL: Byte = 0
    private val MAGIC = "TAG".toByteArray(Charsets.ISO_8859_1)

    fun read(source: TagSource): RawTags? {
        val tail = source.readTail(TAG_SIZE)
        return if (tail != null && MAGIC.indices.all { tail[it] == MAGIC[it] }) parse(tail) else null
    }

    private fun parse(tag: ByteArray): RawTags {
        // v1.1 gave up the last two comment bytes: a zero, then the track number.
        val track = if (tag[TRACK_MARKER_INDEX] == NUL) tag[TRACK_INDEX].toInt() and BYTE_MASK else 0
        return RawTags(
            title = field(tag, TITLE_START),
            artist = field(tag, ARTIST_START),
            albumArtist = null,
            album = field(tag, ALBUM_START),
            trackNumber = track.takeIf { it > 0 },
            trackTotal = null,
            discNumber = null,
            discTotal = null,
            hasCover = false,
            version = TagVersion.ID3V1,
        )
    }

    private fun field(
        tag: ByteArray,
        start: Int,
    ): String? {
        val bytes = tag.copyOfRange(start, start + FIELD_SIZE)
        val length = bytes.indexOf(NUL).takeIf { it >= 0 } ?: FIELD_SIZE
        return String(bytes, 0, length, Charsets.ISO_8859_1).trim().takeIf { it.isNotEmpty() }
    }
}
