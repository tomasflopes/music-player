package dev.tomasflopes.musicplayer.core.model.playlists

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaylistNameTest {
    @Test
    fun `a name is trimmed`() = assertEquals("Gym", PlaylistName.clean("  Gym \n"))

    @Test
    fun `a blank name is no name`() {
        assertNull(PlaylistName.clean(""))
        assertNull(PlaylistName.clean("   \t"))
    }

    @Test
    fun `a long name is cut to the limit and not left ending in a space`() {
        val raw = "a".repeat(PlaylistName.MAX_LENGTH - 1) + " " + "b".repeat(10)

        val cleaned = PlaylistName.clean(raw)

        assertEquals("a".repeat(PlaylistName.MAX_LENGTH - 1), cleaned)
    }

    @Test
    fun `inner spaces and case are kept`() = assertEquals("Late  Night RUN", PlaylistName.clean("Late  Night RUN"))
}
