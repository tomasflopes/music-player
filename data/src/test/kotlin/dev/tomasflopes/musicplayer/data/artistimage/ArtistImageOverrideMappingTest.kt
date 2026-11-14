package dev.tomasflopes.musicplayer.data.artistimage

import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageChoice
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.data.db.entity.ArtistImageOverrideEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ArtistImageOverrideMappingTest {
    private val id = ArtistId(7)

    @Test
    fun `every choice survives a round trip through its row`() {
        listOf(
            ArtistImageChoice.Remote("https://cdn.test/a.jpg"),
            ArtistImageChoice.LocalFile("7-100.img"),
            ArtistImageChoice.Generated,
        ).forEach { assertEquals(it, it.toEntity(id, 5).toChoice()) }
    }

    @Test
    fun `the row remembers the artist and when it was written`() {
        val row = ArtistImageChoice.Generated.toEntity(id, 123)
        assertEquals(ArtistImageOverrideEntity(7, "generated", null, 123), row)
    }

    @Test
    fun `a row this build cannot read means automatic`() {
        assertNull(ArtistImageOverrideEntity(7, "from-the-future", "x", 1).toChoice())
        assertNull(ArtistImageOverrideEntity(7, "remote", null, 1).toChoice())
    }
}
