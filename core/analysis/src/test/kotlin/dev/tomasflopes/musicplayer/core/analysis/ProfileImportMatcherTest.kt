package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.Normalizer

class ProfileImportMatcherTest {
    private fun target(
        id: Long,
        path: String,
        size: Long = 100,
        inLibrary: Boolean = true,
    ) = ImportTarget(id, path, size, inLibrary)

    private fun matcher(vararg targets: ImportTarget) = ProfileImportMatcher(targets.asSequence(), "Music/")

    @Test
    fun `matches on the path relative to the music root`() {
        val m = matcher(target(1, "Music/Guitarra/Song.mp3"))

        val match = m.match("Guitarra/Song.mp3", 100)

        assertEquals(ProfileMatch(songId = 1, counted = true), match)
    }

    @Test
    fun `a root written with or without its trailing slash matches alike`() {
        val withSlash = ProfileImportMatcher(sequenceOf(target(1, "Music/Rock/a.mp3")), "Music/")
        val withoutSlash = ProfileImportMatcher(sequenceOf(target(1, "Music/Rock/a.mp3")), "Music")

        assertEquals(1L, withSlash.match("Rock/a.mp3", 100)?.songId)
        assertEquals(1L, withoutSlash.match("Rock/a.mp3", 100)?.songId)
    }

    @Test
    fun `a decomposed NFD name on the phone matches the composed NFC name in the export`() {
        val nfd = Normalizer.normalize("Music/Nós/Ação.mp3", Normalizer.Form.NFD)
        val nfc = Normalizer.normalize("Nós/Ação.mp3", Normalizer.Form.NFC)
        assertFalse(nfd == nfc)

        val match = matcher(target(7, nfd)).match(nfc, 100)

        assertEquals(7L, match?.songId)
    }

    @Test
    fun `a composed export name matches a decomposed library name and the reverse`() {
        val nfcInLibrary = Normalizer.normalize("Music/Café.mp3", Normalizer.Form.NFC)
        val nfdExport = Normalizer.normalize("Café.mp3", Normalizer.Form.NFD)

        assertEquals(3L, matcher(target(3, nfcInLibrary)).match(nfdExport, 100)?.songId)
    }

    @Test
    fun `a different size is not a match and the song stays unmatched`() {
        val m = matcher(target(1, "Music/a.mp3", size = 100))

        assertNull(m.match("a.mp3", 101))
    }

    @Test
    fun `a path outside the music root never matches`() {
        val m = matcher(target(1, "Podcasts/a.mp3"))

        assertNull(m.match("a.mp3", 100))
        assertNull(m.match("Podcasts/a.mp3", 100))
    }

    @Test
    fun `a retired song is matched but not counted`() {
        val m = matcher(target(9, "Music/Old/gone.mp3", inLibrary = false))

        val match = m.match("Old/gone.mp3", 100)

        assertEquals(ProfileMatch(songId = 9, counted = false), match)
        assertFalse(match!!.counted)
    }

    @Test
    fun `when a retired and a live row share a path and size the live one wins`() {
        val retired = target(4, "Music/Dup/x.mp3", inLibrary = false)
        val live = target(5, "Music/Dup/x.mp3", inLibrary = true)

        val forwardOrder = matcher(retired, live).match("Dup/x.mp3", 100)
        val reverseOrder = matcher(live, retired).match("Dup/x.mp3", 100)

        assertEquals(5L, forwardOrder?.songId)
        assertEquals(5L, reverseOrder?.songId)
        assertTrue(forwardOrder!!.counted)
    }

    @Test
    fun `the match uses the exact size and never a media store id`() {
        val m = matcher(target(1, "Music/a.mp3", size = 2_955_052))

        assertEquals(1L, m.match("a.mp3", 2_955_052)?.songId)
        assertNull(m.match("a.mp3", 2_955_051))
    }
}
