package dev.tomasflopes.musicplayer.core.model.log

import dev.tomasflopes.musicplayer.core.model.log.PlaySource.ALBUM
import dev.tomasflopes.musicplayer.core.model.log.PlaySource.ARTIST
import dev.tomasflopes.musicplayer.core.model.log.PlaySource.ARTIST_SHUFFLE
import dev.tomasflopes.musicplayer.core.model.log.PlaySource.SHUFFLE_ALL
import dev.tomasflopes.musicplayer.core.model.log.PlaySource.SONGS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaySourceTest {
    @Test
    fun `source wire values are the design's 13 plus songs and artist`() {
        val expected =
            setOf(
                "shuffle_all",
                "artist_shuffle",
                "search",
                "album",
                "folder",
                "recently_added",
                "mix",
                "radio",
                "discover",
                "rewind",
                "top100",
                "playlist",
                "queue",
                "songs",
                "artist",
            )

        assertEquals(expected, PlaySource.entries.map { it.wire }.toSet())
    }

    @Test
    fun `shuffle relabels only the two paired sources`() {
        assertEquals(ARTIST_SHUFFLE, ARTIST.withShuffle(true))
        assertEquals(ARTIST, ARTIST_SHUFFLE.withShuffle(false))
        assertEquals(SHUFFLE_ALL, SONGS.withShuffle(true))
        assertEquals(SONGS, SHUFFLE_ALL.withShuffle(false))
        assertEquals(ALBUM, ALBUM.withShuffle(true))
        assertEquals(ARTIST_SHUFFLE, ARTIST_SHUFFLE.withShuffle(true))
        assertEquals(SONGS, SONGS.withShuffle(false))
    }

    @Test
    fun `ended-by and started-by wire values are the log's`() {
        assertEquals(listOf("completed", "skipped", "stopped"), EndedBy.entries.map { it.wire })
        assertEquals(listOf("picked", "auto", "nav"), StartedBy.entries.map { it.wire })
    }

    @Test
    fun `wire values are unique, so they can be read back`() {
        assertEquals(
            PlaySource.entries.size,
            PlaySource.entries
                .map { it.wire }
                .toSet()
                .size,
        )
        assertEquals(ALBUM, PlaySource.fromWire("album"))
        assertEquals(StartedBy.NAV, StartedBy.fromWire("nav"))
        assertEquals(EndedBy.SKIPPED, EndedBy.fromWire("skipped"))
    }
}
