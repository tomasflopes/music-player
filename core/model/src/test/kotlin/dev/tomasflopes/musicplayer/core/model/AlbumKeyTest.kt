package dev.tomasflopes.musicplayer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AlbumKeyTest {
    @Test
    fun `same album title, different album artists, are two albums`() {
        assertNotEquals(AlbumKey.of("Same Name", "Sample A", "x"), AlbumKey.of("Same Name", "Sample B", "x"))
    }

    @Test
    fun `same album title and album artist, different song artists, are one album`() {
        assertEquals(AlbumKey.of("Compilation", "Various", "A"), AlbumKey.of("Compilation", "Various", "B"))
    }

    @Test
    fun `blank album artist falls back to artist`() {
        assertEquals(AlbumKey("DAMN.", "Kendrick Lamar"), AlbumKey.of("DAMN.", " ", "Kendrick Lamar"))
    }

    @Test
    fun `missing album artist and artist fall back to unknown artist`() {
        assertEquals(AlbumKey("Album", DisplayFallbacks.UNKNOWN_ARTIST), AlbumKey.of("Album", null, null))
    }

    @Test
    fun `blank album is no album`() {
        assertNull(AlbumKey.of("  ", "A", "A"))
    }

    @Test
    fun `missing album is no album`() {
        assertNull(AlbumKey.of(null, "A", "A"))
    }

    @Test
    fun `key is case-sensitive, as tagged`() {
        assertNotEquals(AlbumKey.of("DAMN.", "K", null), AlbumKey.of("Damn.", "K", null))
    }
}
