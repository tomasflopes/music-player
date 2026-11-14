package dev.tomasflopes.musicplayer.core.artistimage

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnlineModeTest {
    @Test
    fun `off never allows a request`() {
        NetworkKind.entries.forEach { assertEquals(false, ArtistPhotoMode.OFF.allowsRequest(it)) }
    }

    @Test
    fun `wifi only allows an unmetered connection only`() {
        val allowed = NetworkKind.entries.filter { ArtistPhotoMode.WIFI_ONLY.allowsRequest(it) }
        assertEquals(listOf(NetworkKind.UNMETERED), allowed)
    }

    @Test
    fun `always allows any connection but none`() {
        val allowed = NetworkKind.entries.filter { ArtistPhotoMode.ALWAYS.allowsRequest(it) }
        assertEquals(listOf(NetworkKind.METERED, NetworkKind.UNMETERED), allowed)
    }

    @Test
    fun `the policy follows the setting and the connection`() =
        runTest {
            val settings = FakeSettings(ArtistPhotoMode.WIFI_ONLY)
            var network = NetworkKind.METERED
            val policy = ModeOnlinePolicy(settings) { network }
            assertEquals(false, policy.allowed())
            network = NetworkKind.UNMETERED
            assertEquals(true, policy.allowed())
            settings.setMode(ArtistPhotoMode.OFF)
            assertEquals(false, policy.allowed())
            settings.setMode(ArtistPhotoMode.ALWAYS)
            network = NetworkKind.METERED
            assertEquals(true, policy.allowed())
        }

    @Test
    fun `off does not even read the connection`() =
        runTest {
            val policy = ModeOnlinePolicy(FakeSettings(ArtistPhotoMode.OFF)) { error("must not be read") }
            assertEquals(false, policy.allowed())
        }

    @Test
    fun `initials are the first letters of the first two words`() {
        assertEquals("RV", initialsOf("Rui Veloso"))
        assertEquals("TB", initialsOf("The Beatles and more"))
        assertEquals("Q", initialsOf("Queen"))
        assertEquals("AR", initialsOf("A\$AP Rocky"))
        assertEquals("É", initialsOf("édith"))
        assertEquals("2", initialsOf("2Pac"))
        assertEquals("?", initialsOf("???"))
        assertEquals("?", initialsOf(""))
    }
}
