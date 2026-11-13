package dev.tomasflopes.musicplayer.data.scan

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.core.model.ports.LibraryScanController
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Asks for a rescan when the app starts, when the music folder changes, and when MediaStore reports a change
 * (debounced, so a folder copy of 200 files is one scan). Call [start] once from the Application.
 */
class MediaStoreObserver(
    private val resolver: ContentResolver,
    private val settings: SettingsRepository,
    private val scans: LibraryScanController,
    private val config: LibraryConfig,
    private val scope: CoroutineScope,
) {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(
                selfChange: Boolean,
                uri: Uri?,
            ) {
                changes.tryEmit(Unit)
            }
        }

    @OptIn(FlowPreview::class)
    fun start() {
        resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        changes.debounce(config.mediaStoreDebounceMs).onEach { scans.requestScan() }.launchIn(scope)
        settings.musicRoot
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { scans.requestScan() }
            .launchIn(scope)
    }
}
