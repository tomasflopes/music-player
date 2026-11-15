package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileCodecsTest {
    private fun sample(seed: Int): SoundProfile =
        SoundProfile(
            fixed = FloatArray(SoundProfile.FIXED_SIZE) { seed + it * 0.1f + 1e-5f },
            energyCurve = FloatArray(seed + 3) { it * 0.01f },
        )

    @Test
    fun `the fixed layout has the sizes of ADR 0065`() {
        assertEquals(33, SoundProfile.FIXED_SIZE)
        assertEquals(SoundProfile.FIXED_SIZE, SoundProfile.FIXED_NAMES.size)
        assertEquals("tempo_bpm", SoundProfile.FIXED_NAMES.first())
        assertEquals("music_end_s", SoundProfile.FIXED_NAMES.last())
    }

    @Test
    fun `the blob round trips and its length is implied`() {
        val profile = sample(2)

        val bytes = ProfileBlobCodec.encode(profile)

        assertEquals((33 + profile.energyCurve.size) * 4, bytes.size)
        assertEquals(profile, ProfileBlobCodec.decode(bytes))
        assertEquals(
            0,
            ProfileBlobCodec
                .decode(
                    ProfileBlobCodec.encode(
                        sample(-3).let {
                            SoundProfile(it.fixed, FloatArray(0))
                        },
                    ),
                ).energyCurve.size,
        )
    }

    @Test
    fun `bytes that cannot be a profile are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ProfileBlobCodec.decode(ByteArray(7)) }
        assertThrows(IllegalArgumentException::class.java) { ProfileBlobCodec.decode(ByteArray(4 * 32)) }
    }

    @Test
    fun `the fixed part can be read from the first 33 floats alone`() {
        val profile = sample(4)
        val bytes = ProfileBlobCodec.encode(profile)

        val head = bytes.copyOf(SoundProfile.FIXED_SIZE * 4)

        assertTrue(profile.fixed.contentEquals(ProfileBlobCodec.decodeFixed(head)))
        assertTrue(profile.fixed.contentEquals(ProfileBlobCodec.decodeFixed(bytes)))
    }

    @Test
    fun `a head shorter than the fixed part is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ProfileBlobCodec.decodeFixed(ByteArray(4 * 32)) }
    }

    @Test
    fun `an export round trips every float`() {
        val header = ExportHeader(profilerVersion = 1, musicRootHint = "Music")
        val records =
            listOf(ExportRecord("A/é.mp3", 123L, 200_000L, sample(1)), ExportRecord("B.mp3", 9L, 1L, sample(5)))
        val lines = listOf(ProfileExportCodec.headerLine(header)) + records.map { ProfileExportCodec.songLine(it) }

        val read = ProfileExportCodec.read(lines)

        assertEquals(header, read.header)
        assertFalse(read.truncatedTail)
        assertEquals(records.map { it.path }, read.records.map { it.path })
        assertEquals(records.map { it.profile }, read.records.map { it.profile })
        assertEquals(records.map { it.size to it.durationMs }, read.records.map { it.size to it.durationMs })
    }

    @Test
    fun `a half written last line is ignored and flagged`() {
        val header = ProfileExportCodec.headerLine(ExportHeader(1, "Music"))
        val good = ProfileExportCodec.songLine(ExportRecord("a.mp3", 1L, 2L, sample(1)))
        val half = ProfileExportCodec.songLine(ExportRecord("b.mp3", 1L, 2L, sample(2))).dropLast(40)

        val read = ProfileExportCodec.read(listOf(header, good, half))

        assertEquals(listOf("a.mp3"), read.records.map { it.path })
        assertTrue(read.truncatedTail)
    }

    @Test
    fun `a broken line in the middle is an error`() {
        val header = ProfileExportCodec.headerLine(ExportHeader(1, "Music"))
        val good = ProfileExportCodec.songLine(ExportRecord("a.mp3", 1L, 2L, sample(1)))

        assertThrows(ExportFormatException::class.java) { ProfileExportCodec.read(listOf(header, "{oops", good)) }
    }

    @Test
    fun `a file without a header is rejected`() {
        assertThrows(ExportFormatException::class.java) { ProfileExportCodec.read(emptyList()) }
        assertThrows(ExportFormatException::class.java) { ProfileExportCodec.read(listOf("""{"path":"a.mp3"}""")) }
    }
}
