package dev.tomasflopes.musicplayer.data.scan

import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TreeUriMusicRootResolverTest {
    private val resolver = TreeUriMusicRootResolver()

    @Test
    fun `a picked folder keeps its tree uri and gains its MediaStore location`() {
        assertEquals(
            MusicRoot("content://tree/primary%3AMusic", "external_primary", "Music/"),
            resolver.resolve("content://tree/primary%3AMusic", "primary:Music"),
        )
    }

    @Test
    fun `a document id of an unknown shape gives null`() {
        assertNull(resolver.resolve("content://tree/downloads", "downloads"))
    }
}
