package dev.tomasflopes.musicplayer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.tomasflopes.musicplayer.data.scan.MediaStoreObserver
import javax.inject.Inject

@HiltAndroidApp
class MusicPlayerApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var mediaStoreObserver: MediaStoreObserver

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Rescan on app start, on MediaStore changes and on music-root changes (ADR 0014).
        mediaStoreObserver.start()
    }
}
