package dev.tomasflopes.musicplayer.core.tags

import java.io.IOException
import java.io.InputStream
import java.util.Locale

/** A picture embedded in a file, exactly as stored. */
class Cover(
    val bytes: ByteArray,
    val mimeType: String,
)

/** Reads the cover embedded in one file. Never throws: a file without a usable picture gives `null`. */
interface CoverReader {
    /** The front cover if the file has one, otherwise its first picture. */
    fun readCover(source: TagSource): Cover?
}

/** Covers from ID3v2 `APIC` / `PIC` frames. A picture larger than [maxCoverBytes] is skipped, not loaded. */
class Id3CoverReader(
    private val maxCoverBytes: Int = DEFAULT_MAX_COVER_BYTES,
) : CoverReader {
    override fun readCover(source: TagSource): Cover? =
        try {
            source.openStream().buffered().use(::frontOrFirstPicture)
        } catch (ignored: IOException) {
            null
        } catch (ignored: RuntimeException) {
            // Same contract as Id3TagReader: a damaged file is no cover, never a crash.
            null
        }

    private fun frontOrFirstPicture(file: InputStream): Cover? {
        var first: Picture? = null
        for (picture in pictures(file)) {
            if (picture.type == FRONT_COVER) return picture.cover
            first = first ?: picture
        }
        return first?.cover
    }

    private fun pictures(file: InputStream): Sequence<Picture> {
        val header = Id3v2Header.parse(file.readNBytes(Id3v2Header.SIZE)) ?: return emptySequence()
        val frames = Id3v2Frames(header, TagBodyStream(file, header.tagSize), mutableListOf())
        return generateSequence(frames::next)
            .filter { it.id in PICTURE_FRAME_IDS && it.unreadableBecause == null && it.maxSize <= maxCoverBytes }
            .mapNotNull { frame -> frames.payload(frame)?.let { Picture.parse(frame.id, it) } }
    }

    private companion object {
        const val DEFAULT_MAX_COVER_BYTES = 16 * 1024 * 1024
        const val FRONT_COVER = 3
    }
}

/**
 * Content of a picture frame: text encoding, image format, picture type, a description, then the image.
 * v2.2 gives the format as three letters (`PNG`); later versions as a NUL-terminated mime type.
 */
private class Picture(
    val type: Int,
    val cover: Cover,
) {
    companion object {
        private const val V2_2_PICTURE = "PIC"
        private const val V2_2_FORMAT_END = 4
        private const val UTF16_WITH_BOM: Byte = 1
        private const val UTF16_BIG_ENDIAN: Byte = 2
        private const val NUL: Byte = 0
        private const val BYTE_MASK = 0xFF

        /** `null` when the frame is too short to hold an image. */
        fun parse(
            frameId: String,
            payload: ByteArray,
        ): Picture? {
            val formatEnd = if (frameId == V2_2_PICTURE) V2_2_FORMAT_END else payload.indexOfFrom(NUL, 1)
            val typeIndex = if (frameId == V2_2_PICTURE) formatEnd else formatEnd + 1
            val imageStart =
                if (formatEnd < 1 || typeIndex >= payload.size) payload.size else descriptionEnd(payload, typeIndex + 1)
            return if (imageStart >= payload.size) {
                null
            } else {
                val format = String(payload, 1, formatEnd - 1, Charsets.ISO_8859_1)
                Picture(
                    type = payload[typeIndex].toInt() and BYTE_MASK,
                    cover = Cover(payload.copyOfRange(imageStart, payload.size), mimeType(format)),
                )
            }
        }

        /** Index just past the description's terminator: one NUL, or an aligned pair of NULs in UTF-16. */
        private fun descriptionEnd(
            payload: ByteArray,
            start: Int,
        ): Int {
            val wide = payload[0] == UTF16_WITH_BOM || payload[0] == UTF16_BIG_ENDIAN
            val terminator =
                if (wide) {
                    (start until payload.size - 1 step 2).firstOrNull { payload[it] == NUL && payload[it + 1] == NUL }
                } else {
                    payload.indexOfFrom(NUL, start).takeIf { it >= 0 }
                }
            return if (terminator == null) payload.size else terminator + if (wide) 2 else 1
        }

        private fun ByteArray.indexOfFrom(
            byte: Byte,
            start: Int,
        ): Int = (start until size).firstOrNull { this[it] == byte } ?: -1

        /** `PNG`, `image/png`, `JPG` and `image/jpg` all occur in real files. */
        private fun mimeType(format: String): String {
            val subtype = format.trim().lowercase(Locale.ROOT).substringAfter('/')
            return "image/" + if (subtype == "jpg") "jpeg" else subtype.ifEmpty { "*" }
        }
    }
}
