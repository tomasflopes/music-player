package dev.tomasflopes.musicplayer.ui.settings

import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MusicRootDisplayTest {
    @Test
    fun `a folder on internal storage shows its path under Internal storage`() {
        val music = MusicRoot("t", "external_primary", "Music/")
        val nested = MusicRoot("t", "external_primary", "Music/HipHop/")

        assertEquals("Internal storage/Music", music.displayPath())
        assertEquals("Internal storage/Music/HipHop", nested.displayPath())
    }

    @Test
    fun `a whole volume shows only the volume`() {
        assertEquals("Internal storage", MusicRoot("t", "external_primary", "").displayPath())
    }

    @Test
    fun `a folder on another volume names the volume by its id`() {
        assertEquals("Storage 1A2B-3C4D/Music", MusicRoot("t", "1a2b-3c4d", "Music/").displayPath())
    }
}
