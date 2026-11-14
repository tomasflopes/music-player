package dev.tomasflopes.musicplayer.data.artistimage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.tomasflopes.musicplayer.core.artistimage.ArtistPhotoMode
import dev.tomasflopes.musicplayer.core.artistimage.ArtistPhotoSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** "Artist photos from the internet" in the same Preferences DataStore as the other settings. Default: Wi-Fi only. */
class DataStoreArtistPhotoSettings(
    private val store: DataStore<Preferences>,
) : ArtistPhotoSettings {
    override val mode: Flow<ArtistPhotoMode> =
        store.data
            .map { preferences ->
                ArtistPhotoMode.entries.firstOrNull { it.name == preferences[MODE] }
                    ?: ArtistPhotoMode.DEFAULT
            }.distinctUntilChanged()

    override suspend fun setMode(mode: ArtistPhotoMode) {
        store.edit { it[MODE] = mode.name }
    }

    private companion object {
        val MODE = stringPreferencesKey("artist_photos_mode")
    }
}
