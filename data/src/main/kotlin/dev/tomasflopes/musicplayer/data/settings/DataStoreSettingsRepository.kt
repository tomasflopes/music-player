package dev.tomasflopes.musicplayer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The two phase 1 settings, in Preferences DataStore. Days are coerced into [LibraryConfig.recentlyAddedDaysRange]. */
class DataStoreSettingsRepository(
    private val store: DataStore<Preferences>,
    private val config: LibraryConfig,
) : SettingsRepository {
    override val musicRoot: Flow<MusicRoot?> =
        store.data
            .map { preferences ->
                val treeUri = preferences[MUSIC_ROOT_TREE_URI]
                val volume = preferences[MUSIC_ROOT_VOLUME]
                val relativePath = preferences[MUSIC_ROOT_RELATIVE_PATH]
                if (treeUri == null || volume == null || relativePath == null) {
                    null
                } else {
                    MusicRoot(treeUri, volume, relativePath)
                }
            }.distinctUntilChanged()

    override val recentlyAddedDays: Flow<Int> =
        store.data
            .map { it[RECENTLY_ADDED_DAYS] ?: config.recentlyAddedDefaultDays }
            .distinctUntilChanged()

    override suspend fun setMusicRoot(root: MusicRoot) {
        store.edit { preferences ->
            preferences[MUSIC_ROOT_TREE_URI] = root.treeUri
            preferences[MUSIC_ROOT_VOLUME] = root.volumeName
            preferences[MUSIC_ROOT_RELATIVE_PATH] = root.relativePath
        }
    }

    override suspend fun setRecentlyAddedDays(days: Int) {
        store.edit { it[RECENTLY_ADDED_DAYS] = days.coerceIn(config.recentlyAddedDaysRange) }
    }

    private companion object {
        val MUSIC_ROOT_TREE_URI = stringPreferencesKey("music_root_tree_uri")
        val MUSIC_ROOT_VOLUME = stringPreferencesKey("music_root_volume")
        val MUSIC_ROOT_RELATIVE_PATH = stringPreferencesKey("music_root_relative_path")
        val RECENTLY_ADDED_DAYS = intPreferencesKey("recently_added_days")
    }
}
