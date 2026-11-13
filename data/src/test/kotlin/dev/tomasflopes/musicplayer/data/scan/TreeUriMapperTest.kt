package dev.tomasflopes.musicplayer.data.scan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TreeUriMapperTest {
    @Test
    fun `primary subfolder`() {
        assertEquals(
            MusicRootLocation("external_primary", "Music/HipHop/"),
            TreeUriMapper.fromTreeDocumentId("primary:Music/HipHop"),
        )
    }

    @Test
    fun `primary root`() {
        assertEquals(MusicRootLocation("external_primary", ""), TreeUriMapper.fromTreeDocumentId("primary:"))
    }

    @Test
    fun `sd card volume id is lower-cased`() {
        assertEquals(MusicRootLocation("1a2b-3c4d", "Music/"), TreeUriMapper.fromTreeDocumentId("1A2B-3C4D:Music"))
    }

    @Test
    fun `unknown id shape gives null`() {
        assertNull(TreeUriMapper.fromTreeDocumentId("downloads"))
    }

    @Test
    fun `like pattern escapes wildcards`() {
        assertEquals(
            "Music/100\\% Rap\\_Mix/%",
            TreeUriMapper.likePattern(MusicRootLocation("external_primary", "Music/100% Rap_Mix/")),
        )
    }
}
