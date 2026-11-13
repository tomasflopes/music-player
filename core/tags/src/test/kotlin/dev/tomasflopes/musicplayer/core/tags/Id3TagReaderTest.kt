package dev.tomasflopes.musicplayer.core.tags

import dev.tomasflopes.musicplayer.core.tags.TextEncoding.UTF16
import dev.tomasflopes.musicplayer.core.tags.TextEncoding.UTF8
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Id3TagReaderTest {
    private val reader = Id3TagReader()

    private fun success(file: ByteArray): TagReadResult.Success =
        assertInstanceOf(TagReadResult.Success::class.java, reader.read(ByteArrayTagSource(file)))

    private fun read(file: ByteArray): RawTags = success(file).tags

    private val longTitle = "HUMBLE".repeat(50)

    @Test
    fun `v2_3 utf16 album track`() {
        val tags =
            read(
                id3v23 {
                    text("TIT2", "HUMBLE.", UTF16)
                    text("TPE1", "Kendrick Lamar", UTF16)
                    text("TPE2", "Kendrick Lamar", UTF16)
                    text("TALB", "DAMN.", UTF16)
                    text("TRCK", "8/14")
                    text("TPOS", "1/1")
                },
            )

        val expected =
            RawTags("HUMBLE.", "Kendrick Lamar", "Kendrick Lamar", "DAMN.", 8, 14, 1, 1, false, TagVersion.ID3V2_3)
        assertEquals(expected, tags)
    }

    @Test
    fun `v2_4 utf8 with synchsafe frame sizes`() {
        val tags =
            read(
                id3v24 {
                    text("TIT2", longTitle, UTF8)
                    text("TPE1", "Kendrick Lamar", UTF8)
                    text("TPE2", "Kendrick Lamar", UTF8)
                    text("TALB", "DAMN.", UTF8)
                    text("TRCK", "8/14")
                    text("TPOS", "1/1")
                },
            )

        val expected =
            RawTags(longTitle, "Kendrick Lamar", "Kendrick Lamar", "DAMN.", 8, 14, 1, 1, false, TagVersion.ID3V2_4)
        assertEquals(expected, tags)
    }

    @Test
    fun `v2_4 written with plain 32-bit frame sizes still reads`() {
        val tags =
            read(
                id3v24 {
                    plainFrameSizes()
                    text("TIT2", longTitle, UTF8)
                    text("TRCK", "3")
                },
            )

        assertEquals(longTitle, tags.title)
        assertEquals(3, tags.trackNumber)
    }

    @Test
    fun `v2_4 plain 32-bit size on the last frame still reads`() {
        val tags =
            read(
                id3v24 {
                    plainFrameSizes()
                    text("TRCK", "3")
                    text("TIT2", longTitle, UTF8)
                    padding(32)
                },
            )

        assertEquals(longTitle, tags.title)
    }

    @Test
    fun `v2_2 three-letter frames`() {
        val tags =
            read(
                id3v22 {
                    text("TT2", "Song")
                    text("TP1", "Artist")
                    text("TP2", "Album Artist")
                    text("TAL", "Album")
                    text("TRK", "3")
                    text("TPA", "2")
                },
            )

        val expected = RawTags("Song", "Artist", "Album Artist", "Album", 3, null, 2, null, false, TagVersion.ID3V2_2)
        assertEquals(expected, tags)
    }

    @Test
    fun `tag-level unsynchronisation is undone`() {
        val tags =
            read(
                id3v23 {
                    unsynchronised()
                    text("TIT2", "ÿes ÿ", UTF16)
                    text("TRCK", "3")
                },
            )

        assertEquals("ÿes ÿ", tags.title)
        assertEquals(3, tags.trackNumber)
    }

    @Test
    fun `v2_4 frame-level unsynchronisation is undone`() {
        val tags =
            read(
                id3v24 {
                    unsynchronised()
                    text("TIT2", "ÿes ÿ", UTF16)
                    text("TRCK", "3")
                },
            )

        assertEquals("ÿes ÿ", tags.title)
        assertEquals(3, tags.trackNumber)
    }

    @Test
    fun `v2_3 extended header and padding are skipped`() {
        val result =
            success(
                id3v23 {
                    extendedHeader()
                    text("TIT2", "Song")
                    padding(64)
                },
            )

        assertEquals("Song", result.tags.title)
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test
    fun `v2_4 extended header and padding are skipped`() {
        val result =
            success(
                id3v24 {
                    extendedHeader()
                    text("TIT2", "Song")
                    padding(64)
                },
            )

        assertEquals("Song", result.tags.title)
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test
    fun `picture sets hasCover and its bytes are not read into memory`() {
        val fiveMegabytes = 5 * 1024 * 1024
        val limit = 64 * 1024
        val source =
            RecordingTagSource(
                id3v23 {
                    picture(ByteArray(fiveMegabytes) { it.toByte() })
                    text("TIT2", "After the picture")
                },
            )

        val tags = assertInstanceOf(TagReadResult.Success::class.java, reader.read(source)).tags

        assertTrue(tags.hasCover)
        assertEquals("After the picture", tags.title)
        assertTrue(source.largestReadRequest < limit, "largest read was ${source.largestReadRequest} bytes")
        assertTrue(source.bytesRead < limit, "${source.bytesRead} bytes were read into memory")
    }

    @Test
    fun `v2_2 picture sets hasCover`() {
        assertTrue(read(id3v22 { picture(ByteArray(16)) }).hasCover)
    }

    @Test
    fun `compressed frame is skipped with a warning`() {
        val compressed = 0x0080
        val result =
            success(
                id3v23 {
                    rawFrame("TIT2", ByteArray(12), flags = compressed)
                    text("TALB", "DAMN.")
                },
            )

        assertNull(result.tags.title)
        assertEquals("DAMN.", result.tags.album)
        assertEquals(listOf("skipped compressed TIT2"), result.warnings)
    }

    @Test
    fun `encrypted frame is skipped with a warning`() {
        val encrypted = 0x0004
        val result =
            success(
                id3v24 {
                    rawFrame("APIC", ByteArray(12), flags = encrypted)
                    text("TALB", "DAMN.")
                },
            )

        assertFalse(result.tags.hasCover)
        assertEquals("DAMN.", result.tags.album)
        assertEquals(listOf("skipped encrypted APIC"), result.warnings)
    }

    @Test
    fun `v2_4 grouping byte and data length indicator are not part of the text`() {
        val groupedWithDataLength = 0x0041
        val groupByte = bytes("07")
        val dataLength = bytes("00 00 00 05")
        val latin1Song = bytes("00 53 6F 6E 67")

        val tags = read(id3v24 { rawFrame("TIT2", groupByte + dataLength + latin1Song, flags = groupedWithDataLength) })

        assertEquals("Song", tags.title)
    }

    @Test
    fun `text is kept exactly`() {
        val tags =
            read(
                id3v23 {
                    text("TALB", "  DAMN. ")
                    text("TIT2", "damn.")
                    text("TPE1", "A/B")
                },
            )

        assertEquals("DAMN.", tags.album)
        assertEquals("damn.", tags.title)
        assertEquals("A/B", tags.artist)
    }

    @Test
    fun `multi-value artist takes the first and warns`() {
        val result = success(id3v24 { text("TPE1", "Ed\u0000SZA", UTF8) })

        assertEquals("Ed", result.tags.artist)
        assertEquals(listOf("multi-value TPE1"), result.warnings)
    }

    @Test
    fun `missing frames are null, not empty`() {
        val tags =
            read(
                id3v23 {
                    text("TIT2", "Song")
                    text("TALB", "")
                    text("TPE1", "   ")
                },
            )

        assertEquals(RawTags("Song", null, null, null, null, null, null, null, false, TagVersion.ID3V2_3), tags)
    }

    @Test
    fun `first of two frames with the same id wins`() {
        val tags =
            read(
                id3v23 {
                    text("TIT2", "First")
                    text("TIT2", "Second")
                },
            )

        assertEquals("First", tags.title)
    }

    @Test
    fun `unknown frames are skipped without a warning`() {
        val result =
            success(
                id3v23 {
                    text("TXXX", "whatever")
                    rawFrame("PRIV", ByteArray(40))
                    text("TIT2", "Song")
                },
            )

        assertEquals("Song", result.tags.title)
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test
    fun `no v2 tag falls back to ID3v1_1`() {
        val file = SilentMp3.frames(1) + id3v1(title = "Song", artist = "Beyoncé", album = "Album", track = 5)

        val tags = read(file)

        assertEquals(RawTags("Song", "Beyoncé", null, "Album", 5, null, null, null, false, TagVersion.ID3V1), tags)
    }

    @Test
    fun `no tag at all`() {
        assertEquals(TagVersion.NONE, read(SilentMp3.frames(10)).version)
    }

    @Test
    fun `tag size larger than the file is a Failure, not a crash`() {
        val file =
            id3v23 {
                text("TIT2", "Song")
                declaredTagSize(5_000)
            }

        assertInstanceOf(TagReadResult.Failure::class.java, reader.read(ByteArrayTagSource(file)))
    }

    @Test
    fun `tag that ends exactly at the end of the file reads`() {
        assertEquals("Song", read(id3v23 { text("TIT2", "Song") }).title)
    }

    @Test
    fun `frame that claims to be larger than the tag stops the read and keeps what came before`() {
        val titleFrameSize = 15
        val tagEndsInsideTheAlbumFrame = titleFrameSize + 30
        val result =
            success(
                id3v23 {
                    text("TIT2", "Song")
                    text("TALB", "A".repeat(100))
                    declaredTagSize(tagEndsInsideTheAlbumFrame)
                    audio(SilentMp3.frames(1))
                },
            )

        assertEquals("Song", result.tags.title)
        assertNull(result.tags.album)
        assertEquals(listOf("stopped at TALB: frame is larger than the tag"), result.warnings)
    }
}
