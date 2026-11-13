package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MediaItemMapperTest {
    private val mapper = MediaItemMapper()

    @Test
    fun `tag text goes into the item unchanged, symbols and accents included`() {
        val song = song(7, title = "m.A.A.d city", artist = "A\$AP Rocky", album = "LOUD.")

        val item = mapper.fieldsOf(QueueEntry(song.ref, PlaySource.ALBUM, manual = false), song)

        assertEquals("m.A.A.d city", item.title)
        assertEquals("A\$AP Rocky", item.artist)
        assertEquals("LOUD.", item.albumTitle)
        val accented = mapper.fieldsOf(QueueEntry(song.ref, PlaySource.ALBUM, false), song.copy(title = "Beyoncé"))
        assertEquals("Beyoncé", accented.title)
    }

    @Test
    fun `the item plays the song's content uri and is identified by the song id`() {
        val song = song(42)

        val item = mapper.fieldsOf(QueueEntry(song.ref, PlaySource.SONGS, manual = false), song)

        assertEquals(song.contentUri, item.uri)
        assertEquals("42", item.mediaId)
        assertEquals(SongId(42), mapper.songIdOf(item.mediaId))
    }

    @Test
    fun `a song without an album has no album title`() {
        val item = mapper.fieldsOf(QueueEntry(song(1).ref, PlaySource.SONGS, false), song(1, album = null))

        assertNull(item.albumTitle)
    }

    @Test
    fun `artwork points at the song's own cover when it has one`() {
        val with = mapper.fieldsOf(QueueEntry(song(1).ref, PlaySource.SONGS, false), song(1, hasCover = true))
        val without = mapper.fieldsOf(QueueEntry(song(2).ref, PlaySource.SONGS, false), song(2, hasCover = false))

        assertEquals("musicplayer://cover/1", with.artworkUri)
        assertNull(without.artworkUri)
    }
}
