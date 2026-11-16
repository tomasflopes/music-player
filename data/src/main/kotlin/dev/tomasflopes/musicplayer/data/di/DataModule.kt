package dev.tomasflopes.musicplayer.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.core.model.ports.LibraryScanController
import dev.tomasflopes.musicplayer.core.model.ports.MusicRootResolver
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.tags.Id3TagReader
import dev.tomasflopes.musicplayer.data.db.Migrations
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.scan.ContentResolverTagSource
import dev.tomasflopes.musicplayer.data.scan.LibraryScanner
import dev.tomasflopes.musicplayer.data.scan.MediaStoreObserver
import dev.tomasflopes.musicplayer.data.scan.MediaStoreSongLister
import dev.tomasflopes.musicplayer.data.scan.ScanProgressBus
import dev.tomasflopes.musicplayer.data.scan.TreeUriMusicRootResolver
import dev.tomasflopes.musicplayer.data.scan.WorkManagerScanController
import dev.tomasflopes.musicplayer.data.settings.DataStoreSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Wires `:data` into the app. `LibraryConfig` is provided by `:app` (AppModule), so tuning lives in one place. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): MusicDatabase =
        Room
            .databaseBuilder(context, MusicDatabase::class.java, MusicDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    @Singleton
    fun preferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun scanner(
        @ApplicationContext context: Context,
        db: MusicDatabase,
        config: LibraryConfig,
    ): LibraryScanner =
        LibraryScanner(
            lister = MediaStoreSongLister(context.contentResolver),
            tagSources = { file -> ContentResolverTagSource(context.contentResolver, file) },
            tagReader = Id3TagReader(),
            db = db,
            config = config,
        )

    @Provides
    @Singleton
    fun scanController(
        @ApplicationContext context: Context,
        bus: ScanProgressBus,
    ): LibraryScanController = WorkManagerScanController(WorkManager.getInstance(context), bus)

    @Provides
    fun musicRootResolver(): MusicRootResolver = TreeUriMusicRootResolver()

    @Provides
    @Singleton
    fun mediaStoreObserver(
        @ApplicationContext context: Context,
        settings: SettingsRepository,
        scans: LibraryScanController,
        config: LibraryConfig,
    ): MediaStoreObserver =
        MediaStoreObserver(
            resolver = context.contentResolver,
            settings = settings,
            scans = scans,
            config = config,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )

    @Provides
    @Singleton
    fun settings(
        store: DataStore<Preferences>,
        config: LibraryConfig,
    ): SettingsRepository = DataStoreSettingsRepository(store, config)
}
