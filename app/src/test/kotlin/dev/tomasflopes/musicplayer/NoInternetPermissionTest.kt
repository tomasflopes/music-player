package dev.tomasflopes.musicplayer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The app is 100% offline: the manifest that ships must never ask for network access. */
class NoInternetPermissionTest {
    private val manifest = File(checkNotNull(System.getProperty("musicplayer.mergedManifest"))).readText()

    @Test
    fun `merged manifest never requests INTERNET`() {
        assertFalse(manifest.contains("android.permission.INTERNET"), "INTERNET permission found in merged manifest")
    }

    @Test
    fun `merged manifest is the real one`() {
        assertTrue(manifest.contains("dev.tomasflopes.musicplayer"))
    }
}
