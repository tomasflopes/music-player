package dev.tomasflopes.musicplayer.core.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class Id3v1ReaderTest {
    private fun read(file: ByteArray): RawTags? = Id3v1Reader.read(ByteArrayTagSource(file))

    @Test
    fun `v1_1 fields are latin1, cut at the first NUL and trimmed`() {
        val tags = read(SilentMp3.frames(1) + id3v1(title = "Song  ", artist = "Beyoncé", album = "Album", track = 5))

        assertEquals(RawTags("Song", "Beyoncé", null, "Album", 5, null, null, null, false, TagVersion.ID3V1), tags)
    }

    @Test
    fun `v1_0 has no track number`() {
        assertNull(checkNotNull(read(id3v1(title = "Song"))).trackNumber)
    }

    @Test
    fun `field that fills all 30 bytes has no NUL to stop at`() {
        val thirty = "123456789012345678901234567890"

        assertEquals(thirty, checkNotNull(read(id3v1(title = thirty + "cut"))).title)
    }

    @Test
    fun `empty fields are null`() {
        val tags = checkNotNull(read(id3v1()))

        assertEquals(RawTags(null, null, null, null, null, null, null, null, false, TagVersion.ID3V1), tags)
    }

    @Test
    fun `tail that does not start with TAG is not a v1 tag`() {
        assertNull(read(SilentMp3.frames(1)))
    }

    @Test
    fun `file shorter than a v1 tag has none`() {
        assertNull(read(ByteArray(100)))
    }
}
