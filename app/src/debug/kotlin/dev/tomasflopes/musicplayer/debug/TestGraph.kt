package dev.tomasflopes.musicplayer.debug

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.ports.LibraryScanController
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.playback.PlaybackController

/**
 * What the instrumented tests in `androidTest` read from the running app. It lives in the debug source set, not in
 * the test APK: Hilt builds the app's component from the app's own classes, so an entry point declared in the test
 * APK is never part of it and `EntryPointAccessors` fails with a `ClassCastException`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TestGraph {
    fun settings(): SettingsRepository

    fun scanner(): LibraryScanController

    fun songs(): SongReader

    fun playback(): PlaybackController

    fun db(): MusicDatabase
}
