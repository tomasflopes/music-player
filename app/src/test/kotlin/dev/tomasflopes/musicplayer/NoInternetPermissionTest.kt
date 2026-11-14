package dev.tomasflopes.musicplayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The app is offline except for artist photos (ADR 0028): the merged manifest asks for exactly these permissions. */
class NoInternetPermissionTest {
    private val manifest = File(checkNotNull(System.getProperty("musicplayer.mergedManifest"))).readText()

    private val permissions =
        Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .filter { it.startsWith("android.permission.") }
            .toSortedSet()

    @Test
    fun `merged manifest asks only for audio, internet and network state`() {
        val expected =
            sortedSetOf(
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.INTERNET",
                "android.permission.READ_MEDIA_AUDIO",
            )
        val extras = permissions.filterNot { it in expected || it in PLATFORM_ADDED }
        assertEquals(emptyList<String>(), extras, "Unexpected permissions in the merged manifest")
        assertTrue(permissions.containsAll(expected), "Missing permissions: ${expected - permissions}")
    }

    @Test
    fun `merged manifest is the real one`() {
        assertTrue(manifest.contains("dev.tomasflopes.musicplayer"))
    }

    private companion object {
        /** Added by libraries for their own use, never by this app's code (WorkManager, Media3 foreground service). */
        val PLATFORM_ADDED =
            setOf(
                "android.permission.WAKE_LOCK",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.POST_NOTIFICATIONS",
            )
    }
}
