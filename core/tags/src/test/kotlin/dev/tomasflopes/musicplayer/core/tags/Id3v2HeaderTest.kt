package dev.tomasflopes.musicplayer.core.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Id3v2HeaderTest {
    @Test
    fun `synchsafe 00 00 02 01 is 257`() {
        assertEquals(257, synchsafe(0x00, 0x00, 0x02, 0x01))
    }

    @Test
    fun `header ID3 03 00 00 then size`() {
        val header = checkNotNull(Id3v2Header.parse(bytes("49 44 33 03 00 00 00 00 02 01")))

        assertEquals(3, header.major)
        assertEquals(257, header.tagSize)
        assertFalse(header.unsynchronised)
    }

    @Test
    fun `not an ID3 header gives null`() {
        assertNull(Id3v2Header.parse(bytes("FF FB 90 64 00 00 00 00 00 00")))
    }

    @Test
    fun `fewer than ten bytes gives null`() {
        assertNull(Id3v2Header.parse(bytes("49 44 33 03 00 00 00 00 02")))
    }

    @Test
    fun `major 5 is rejected`() {
        assertNull(Id3v2Header.parse(bytes("49 44 33 05 00 00 00 00 02 01")))
    }

    @Test
    fun `FF byte in the size is rejected`() {
        assertNull(Id3v2Header.parse(bytes("49 44 33 03 00 00 00 FF 02 01")))
    }

    @Test
    fun `v2_4 flags give unsynchronisation, extended header and footer`() {
        val header = checkNotNull(Id3v2Header.parse(bytes("49 44 33 04 00 D0 00 00 00 0A")))

        assertTrue(header.unsynchronised)
        assertTrue(header.hasExtendedHeader)
        assertTrue(header.hasFooter)
    }

    @Test
    fun `v2_3 has no footer even when that bit is set`() {
        val header = checkNotNull(Id3v2Header.parse(bytes("49 44 33 03 00 50 00 00 00 0A")))

        assertTrue(header.hasExtendedHeader)
        assertFalse(header.hasFooter)
    }

    @Test
    fun `v2_2 has no extended header, that bit means compression there`() {
        val header = checkNotNull(Id3v2Header.parse(bytes("49 44 33 02 00 40 00 00 00 0A")))

        assertFalse(header.hasExtendedHeader)
    }
}
