package dev.tomasflopes.musicplayer.playback.bass

import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test

/** The audiofx [BassControl] (ADR 0102) against the shared contract; needs a device with an Equalizer. */
class AudioFxBassControlTest {
    @Test
    fun contract() {
        val audioManager =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getSystemService(AudioManager::class.java)
        val made = mutableListOf<AudioFxBassControl>()
        try {
            val failures =
                BassControlContract
                    .checks(depthDb = -24f) {
                        AudioFxBassControl(audioManager.generateAudioSessionId()).also(made::add)
                    }.mapNotNull {
                        runCatching(it.run).exceptionOrNull()?.let { e -> "${it.name}: $e" }
                    }
            check(failures.isEmpty()) { failures.joinToString("; ") }
        } finally {
            made.forEach(AudioFxBassControl::release)
        }
    }
}
