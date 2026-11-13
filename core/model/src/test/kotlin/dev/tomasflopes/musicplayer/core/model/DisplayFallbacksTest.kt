package dev.tomasflopes.musicplayer.core.model

import dev.tomasflopes.musicplayer.core.model.DisplayFallbacks.UNKNOWN_ARTIST
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DisplayFallbacksTest {
    @Test
    fun `untitled song shows its file name without extension`() {
        assertEquals("01 intro", DisplayFallbacks.title(null, "01 intro.mp3"))
    }

    @Test
    fun `tags win over the file name`() {
        assertEquals("HUMBLE.", DisplayFallbacks.title("HUMBLE.", "6LACK - Free.mp3"))
        assertEquals("Kendrick Lamar", DisplayFallbacks.artist("Kendrick Lamar", "6LACK - Free.mp3"))
    }

    @Test
    fun `untagged artist - title file name gives both`() {
        assertEquals("Free", DisplayFallbacks.title(null, "6LACK - Free.mp3"))
        assertEquals("6LACK", DisplayFallbacks.artist(null, "6LACK - Free.mp3"))
    }

    @Test
    fun `splits on the first spaced dash only`() {
        assertEquals("5-30", DisplayFallbacks.artist(null, "5-30 - Eu Já Estive Aqui.mp3"))
        assertEquals("Eu Já Estive Aqui", DisplayFallbacks.title(null, "5-30 - Eu Já Estive Aqui.mp3"))
        assertEquals("B - C", DisplayFallbacks.title(null, "A - B - C.mp3"))
    }

    @Test
    fun `featured artists stay in the title for the feature parser`() {
        val fileName = "Perigo Público - 1991 feat. Napoleão Mira.mp3"

        assertEquals("1991 feat. Napoleão Mira", DisplayFallbacks.title(null, fileName))
    }

    @Test
    fun `a leading track number is not an artist`() {
        assertEquals(UNKNOWN_ARTIST, DisplayFallbacks.artist(null, "01 - Intro.mp3"))
        assertEquals("01 - Intro", DisplayFallbacks.title(null, "01 - Intro.mp3"))
        assertEquals(UNKNOWN_ARTIST, DisplayFallbacks.artist(null, "07. - Intro.mp3"))
    }

    @Test
    fun `a dash without spaces does not split`() {
        assertEquals(UNKNOWN_ARTIST, DisplayFallbacks.artist(null, "JAY-Z.mp3"))
        assertEquals("JAY-Z", DisplayFallbacks.title(null, "JAY-Z.mp3"))
    }

    @Test
    fun `empty side does not split`() {
        assertEquals(UNKNOWN_ARTIST, DisplayFallbacks.artist(null, " - Title.mp3"))
        assertEquals("Artist -", DisplayFallbacks.title(null, "Artist - .mp3"))
    }

    @Test
    fun `blank tags count as missing`() {
        assertEquals("Free", DisplayFallbacks.title("  ", "6LACK - Free.mp3"))
        assertEquals("6LACK", DisplayFallbacks.artist("", "6LACK - Free.mp3"))
    }

    @Test
    fun `extension is dropped whatever its case, and only the last one`() {
        assertEquals("song.final", DisplayFallbacks.title(null, "song.final.MP3"))
    }

    @Test
    fun `file name with no extension is kept whole`() {
        assertEquals("song", DisplayFallbacks.title(null, "song"))
    }
}
