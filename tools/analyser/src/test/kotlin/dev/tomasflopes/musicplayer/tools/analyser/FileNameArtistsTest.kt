package dev.tomasflopes.musicplayer.tools.analyser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FileNameArtistsTest {
    @Test
    fun `the main artist is the part of the file name before the first dash`() {
        val artists = FileNameArtists.parse("HH/Valete/Valete - Fim Da Ditadura.mp3")

        assertEquals("valete", artists?.main)
        assertEquals(emptySet<String>(), artists?.featured)
    }

    @Test
    fun `featured artists after feat are split at commas`() {
        val artists = FileNameArtists.parse("x/Artist - Title feat. A, B, C.mp3")

        assertEquals("artist", artists?.main)
        assertEquals(setOf("a", "b", "c"), artists?.featured)
    }

    @Test
    fun `ft and a closing bracket are understood`() {
        val artists = FileNameArtists.parse("x/Artist - Title (ft. Guest Star).mp3")

        assertEquals(setOf("guest star"), artists?.featured)
    }

    @Test
    fun `names without separators are matched against the known artists, longest first`() {
        val known = setOf("joint one", "botje", "one")

        val artists = FileNameArtists.parse("Yung Juse - Mister Farioli Remix feat. Joint One Botje.mp3", known)

        assertEquals("yung juse", artists?.main)
        assertEquals(setOf("joint one", "botje"), artists?.featured)
    }

    @Test
    fun `feat inside a word is not a marker`() {
        val artists = FileNameArtists.parse("Artist - Defeat Of Time.mp3")

        assertEquals(emptySet<String>(), artists?.featured)
    }

    @Test
    fun `case and the way an accent is encoded do not matter`() {
        assertEquals(FileNameArtists.parse("Yung Juse - A.MP3")?.main, FileNameArtists.parse("YUNG JUSE - B.mp3")?.main)
        val precomposed = FileNameArtists.parse("Bal\u00e3o - A.mp3")
        assertEquals(precomposed?.main, FileNameArtists.parse("Bala\u0303o - A.mp3")?.main)
    }

    @Test
    fun `a name without a dash has no artist`() {
        assertNull(FileNameArtists.parse("HH/A/s1.mp3"))
    }
}
