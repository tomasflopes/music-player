package dev.tomasflopes.musicplayer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.tomasflopes.musicplayer.data.scan.MediaStoreObserver
import dev.tomasflopes.musicplayer.refill.QueueRefiller
import javax.inject.Inject

@HiltAndroidApp
class MusicPlayerApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var mediaStoreObserver: MediaStoreObserver

    @Inject lateinit var queueRefiller: QueueRefiller

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Rescan on app start, on MediaStore changes and on music-root changes (ADR 0014).
        mediaStoreObserver.start()
        // Collects only the engine's small refill signal; nothing is read until a seeded queue needs a batch
        // (ADR 0078).
        queueRefiller.start()
    }
}
