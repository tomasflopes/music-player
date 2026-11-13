package dev.tomasflopes.musicplayer.core.tags

import java.io.IOException
import java.io.InputStream

/**
 * Reads ID3v2.2, v2.3 and v2.4 tags from the start of a file, and ID3v1 from its end when there is no v2 tag.
 * The file is read front to back once; cover art is skipped, never loaded.
 */
class Id3TagReader : TagReader {
    override fun read(source: TagSource): TagReadResult =
        try {
            source.openStream().buffered(STREAM_BUFFER_BYTES).use { read(it, source) }
        } catch (e: IOException) {
            TagReadResult.Failure("cannot read the file: $e")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: RuntimeException,
        ) {
            // The contract is "never throws". Damaged tags are handled on purpose (Id3RobustnessTest proves it);
            // this is only the net under that, so one odd file can never stop a 16k-song scan.
            TagReadResult.Failure("internal error: $e")
        }

    private fun read(
        file: InputStream,
        source: TagSource,
    ): TagReadResult {
        val header = Id3v2Header.parse(file.readNBytes(Id3v2Header.SIZE))
        return if (header == null) {
            TagReadResult.Success(Id3v1Reader.read(source) ?: UNTAGGED, emptyList())
        } else {
            readV2(header, file)
        }
    }

    private fun readV2(
        header: Id3v2Header,
        file: InputStream,
    ): TagReadResult {
        val warnings = mutableListOf<String>()
        val body = TagBodyStream(file, header.tagSize)
        val frames = Id3v2Frames(header, body, warnings)
        val fields = Id3v2Fields(frames, warnings)
        generateSequence(frames::next).forEach(fields::take)
        body.skipToEnd()
        return if (body.truncated) {
            TagReadResult.Failure("the ID3v2 tag is larger than the file")
        } else {
            TagReadResult.Success(fields.toRawTags(VERSIONS.getValue(header.major)), warnings)
        }
    }

    private companion object {
        const val STREAM_BUFFER_BYTES = 8 * 1024
        val VERSIONS = mapOf(2 to TagVersion.ID3V2_2, 3 to TagVersion.ID3V2_3, 4 to TagVersion.ID3V2_4)
        val UNTAGGED = RawTags(null, null, null, null, null, null, null, null, false, TagVersion.NONE)
    }
}

private enum class TextField { TITLE, ARTIST, ALBUM_ARTIST, ALBUM, TRACK, DISC }

/** Frame ids of v2.3/v2.4 (four letters) and of v2.2 (three letters) for the six text fields the app uses. */
private val TEXT_FRAMES =
    mapOf(
        "TIT2" to TextField.TITLE,
        "TT2" to TextField.TITLE,
        "TPE1" to TextField.ARTIST,
        "TP1" to TextField.ARTIST,
        "TPE2" to TextField.ALBUM_ARTIST,
        "TP2" to TextField.ALBUM_ARTIST,
        "TALB" to TextField.ALBUM,
        "TAL" to TextField.ALBUM,
        "TRCK" to TextField.TRACK,
        "TRK" to TextField.TRACK,
        "TPOS" to TextField.DISC,
        "TPA" to TextField.DISC,
    )

/** `APIC` in v2.3/v2.4, `PIC` in v2.2. */
internal val PICTURE_FRAME_IDS = setOf("APIC", "PIC")

/** No text the app shows comes close; anything larger is damage, and is skipped instead of allocated. */
private const val MAX_TEXT_FRAME_BYTES = 16 * 1024
private const val BYTE_MASK = 0xFF

/** Collects the frames the app cares about. The first frame of each kind wins. */
private class Id3v2Fields(
    private val frames: Id3v2Frames,
    private val warnings: MutableList<String>,
) {
    private val texts = mutableMapOf<TextField, String>()
    private var hasCover = false

    fun take(frame: Id3v2Frame) {
        val field = TEXT_FRAMES[frame.id]
        when {
            field == null && frame.id !in PICTURE_FRAME_IDS -> Unit
            frame.unreadableBecause != null -> warnings += "skipped ${frame.unreadableBecause} ${frame.id}"
            field == null -> hasCover = true
            frame.maxSize > MAX_TEXT_FRAME_BYTES -> warnings += "skipped oversized ${frame.id}"
            else -> frames.payload(frame)?.let { text(field, frame.id, it) }
        }
    }

    /** Kept exactly as tagged: only surrounding whitespace goes. Empty text is the same as no frame. */
    private fun text(
        field: TextField,
        id: String,
        payload: ByteArray,
    ) {
        val values =
            if (payload.isEmpty()) {
                emptyList()
            } else {
                Id3TextDecoder.decode(payload[0].toInt() and BYTE_MASK, payload.copyOfRange(1, payload.size))
            }
        if (values.size > 1) warnings += "multi-value $id"
        val value = values.firstOrNull()?.trim().orEmpty()
        if (value.isNotEmpty() && field !in texts) texts[field] = value
    }

    fun toRawTags(version: TagVersion): RawTags {
        val (trackNumber, trackTotal) = NumberPair.parse(texts[TextField.TRACK])
        val (discNumber, discTotal) = NumberPair.parse(texts[TextField.DISC])
        return RawTags(
            title = texts[TextField.TITLE],
            artist = texts[TextField.ARTIST],
            albumArtist = texts[TextField.ALBUM_ARTIST],
            album = texts[TextField.ALBUM],
            trackNumber = trackNumber,
            trackTotal = trackTotal,
            discNumber = discNumber,
            discTotal = discTotal,
            hasCover = hasCover,
            version = version,
        )
    }
}
