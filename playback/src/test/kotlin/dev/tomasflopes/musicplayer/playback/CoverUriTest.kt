package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.SongId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CoverUriTest {
    @Test
    fun `a song's cover uri names the song and reads back to its id`() {
        val uri = CoverUri.of(SongId(1234))

        assertEquals("musicplayer://cover/1234", uri)
        assertEquals(SongId(1234), CoverUri.songIdOf(uri))
    }

    @Test
    fun `uris that are not song covers are not ours`() {
        val others =
            listOf(
                "",
                "https://example.com/cover/5",
                "content://media/external/audio/media/5",
                "file:///storage/cover/5",
                "musicplayer://album/5",
                "musicplayer://cover/",
                "musicplayer://cover/abc",
                "musicplayer://cover/5/extra",
                "musicplayer://cover/5?size=1",
            )

        others.forEach { assertNull(CoverUri.songIdOf(it), it) }
    }
}
