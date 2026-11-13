package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry
import dev.tomasflopes.musicplayer.playback.queue.ref
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Songs are named by letter and their ids are the letters' codes, as in the queue tests. */
class UpcomingTest {
    /** Upper case letters are manual entries (Play next / Add to queue), lower case ones come from the list. */
    private fun window(names: String) =
        names.map { name ->
            val manual = name.isUpperCase()
            QueueEntry(ref(name.uppercaseChar()), if (manual) PlaySource.QUEUE else PlaySource.ALBUM, manual)
        }

    private fun libraryOf(names: String) = names.associate { song(it.code.toLong()).id to song(it.code.toLong()) }

    private fun Upcoming.letters() = songs.map { Char(it.id.value.toInt()) }.joinToString("")

    @Test
    fun `songs keep the queue's order`() {
        val upcoming = upcomingOf(window("cab"), libraryOf("ABC"))

        assertEquals("CAB", upcoming.letters())
        assertEquals(0, upcoming.manualCount)
    }

    @Test
    fun `the manual entries right after the current song are counted`() {
        val upcoming = upcomingOf(window("XYa"), libraryOf("XYA"))

        assertEquals("XYA", upcoming.letters())
        assertEquals(2, upcoming.manualCount)
    }

    @Test
    fun `only the leading run of manual entries is counted`() {
        val upcoming = upcomingOf(window("Xay"), libraryOf("XAY"))

        assertEquals(1, upcoming.manualCount)
    }

    @Test
    fun `a song the library no longer has is dropped and not counted`() {
        val upcoming = upcomingOf(window("XYa"), libraryOf("YA"))

        assertEquals("YA", upcoming.letters())
        assertEquals(1, upcoming.manualCount)
    }

    @Test
    fun `an empty window has nothing upcoming`() {
        val upcoming = upcomingOf(emptyList(), emptyMap())

        assertEquals("", upcoming.letters())
        assertEquals(0, upcoming.manualCount)
    }
}
