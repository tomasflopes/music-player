package dev.tomasflopes.musicplayer.core.tags

import dev.tomasflopes.musicplayer.core.tags.Id3TagWriter.Companion.FRONT_COVER
import dev.tomasflopes.musicplayer.core.tags.Id3TagWriter.Companion.OTHER_PICTURE
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CoverReaderTest {
    private val reader: CoverReader = Id3CoverReader()
    private val back = bytes("0B 0A 0C")
    private val front = bytes("0F 00 FF 00 01")

    private fun cover(file: ByteArray): Cover? = reader.readCover(ByteArrayTagSource(file))

    @Test
    fun `front cover wins when there are two pictures`() {
        val cover =
            cover(
                id3v23 {
                    picture(back, type = OTHER_PICTURE)
                    picture(front, type = FRONT_COVER, mimeType = "image/jpeg")
                },
            )

        assertArrayEquals(front, checkNotNull(cover).bytes)
        assertEquals("image/jpeg", cover.mimeType)
    }

    @Test
    fun `first picture is used when none is a front cover`() {
        val cover =
            cover(
                id3v24 {
                    text("TIT2", "Song")
                    picture(back, type = OTHER_PICTURE)
                    picture(front, type = OTHER_PICTURE)
                },
            )

        assertArrayEquals(back, checkNotNull(cover).bytes)
    }

    @Test
    fun `no picture gives null`() {
        assertNull(cover(id3v23 { text("TIT2", "Song") }))
    }

    @Test
    fun `no ID3v2 tag gives null`() {
        assertNull(cover(SilentMp3.frames(1) + id3v1(title = "Song")))
    }

    @Test
    fun `v2_2 picture format becomes a mime type`() {
        val cover = cover(id3v22 { picture(front) })

        assertArrayEquals(front, checkNotNull(cover).bytes)
        assertEquals("image/png", cover.mimeType)
    }

    @Test
    fun `jpg is reported as image jpeg`() {
        assertEquals("image/jpeg", checkNotNull(cover(id3v23 { picture(front, mimeType = "image/jpg") })).mimeType)
    }

    @Test
    fun `utf16 description is skipped up to its two-byte terminator`() {
        val utf16 = bytes("01")
        val mime = "image/png".toByteArray() + bytes("00")
        val frontCover = bytes("03")
        val descriptionCd = bytes("FF FE 43 00 44 00 00 00")

        val cover = cover(id3v23 { rawFrame("APIC", utf16 + mime + frontCover + descriptionCd + front) })

        assertArrayEquals(front, checkNotNull(cover).bytes)
    }

    @Test
    fun `unsynchronised picture comes back as the original bytes`() {
        val cover =
            cover(
                id3v24 {
                    unsynchronised()
                    picture(front)
                },
            )

        assertArrayEquals(front, checkNotNull(cover).bytes)
    }

    @Test
    fun `picture above the size limit is not loaded`() {
        val smallLimit = Id3CoverReader(maxCoverBytes = 1_000)

        assertNull(smallLimit.readCover(ByteArrayTagSource(id3v23 { picture(ByteArray(2_000)) })))
    }

    @Test
    fun `picture frame that is cut short gives null`() {
        val cutInsideThePicture = id3v23 { picture(ByteArray(2_000)) }.copyOf(500)

        assertNull(cover(cutInsideThePicture))
    }

    @Test
    fun `picture frame with nothing after its header fields gives null`() {
        assertNull(cover(id3v23 { picture(ByteArray(0)) }))
    }
}
