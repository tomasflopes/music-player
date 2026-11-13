package dev.tomasflopes.musicplayer.core.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Id3TextDecoderTest {
    @Test
    fun `latin1 e9 is e-acute`() {
        assertEquals(listOf("Beyoncé"), Id3TextDecoder.decode(0, bytes("42 65 79 6F 6E 63 E9")))
    }

    @Test
    fun `utf16 with LE BOM`() {
        assertEquals(listOf("Ed"), Id3TextDecoder.decode(1, bytes("FF FE 45 00 64 00")))
    }

    @Test
    fun `utf16 with BE BOM`() {
        assertEquals(listOf("Ed"), Id3TextDecoder.decode(1, bytes("FE FF 00 45 00 64")))
    }

    @Test
    fun `utf16be without BOM`() {
        assertEquals(listOf("Ed"), Id3TextDecoder.decode(2, bytes("00 45 00 64")))
    }

    @Test
    fun utf8() {
        assertEquals(listOf("Beyoncé"), Id3TextDecoder.decode(3, "Beyoncé".toByteArray()))
    }

    @Test
    fun `trailing NUL dropped, inner NUL splits`() {
        assertEquals(listOf("A", "B"), Id3TextDecoder.decode(3, bytes("41 00 42 00")))
    }

    @Test
    fun `unknown encoding byte falls back to latin1`() {
        val text = bytes("42 65 79 6F 6E 63 E9")

        assertEquals(Id3TextDecoder.decode(0, text), Id3TextDecoder.decode(9, text))
    }

    @Test
    fun `utf16 splits on a two-byte NUL, not on the zero half of a character`() {
        val twoValues = bytes("FF FE 45 00 64 00 00 00 FF FE 53 00 5A 00 41 00 00 00")

        assertEquals(listOf("Ed", "SZA"), Id3TextDecoder.decode(1, twoValues))
    }

    @Test
    fun `utf16 value without its own BOM uses the byte order of the value before it`() {
        assertEquals(listOf("Ed", "SZA"), Id3TextDecoder.decode(1, bytes("FE FF 00 45 00 64 00 00 00 53 00 5A 00 41")))
    }

    @Test
    fun `utf16 without any BOM is read as little endian`() {
        assertEquals(listOf("Ed"), Id3TextDecoder.decode(1, bytes("45 00 64 00")))
    }

    @Test
    fun `odd trailing byte in utf16 is ignored`() {
        assertEquals(listOf("Ed"), Id3TextDecoder.decode(1, bytes("FF FE 45 00 64 00 41")))
    }

    @Test
    fun `no bytes gives no values`() {
        assertEquals(emptyList<String>(), Id3TextDecoder.decode(0, ByteArray(0)))
    }

    @Test
    fun `only NULs gives no values`() {
        assertEquals(emptyList<String>(), Id3TextDecoder.decode(1, bytes("00 00 00 00")))
    }
}
