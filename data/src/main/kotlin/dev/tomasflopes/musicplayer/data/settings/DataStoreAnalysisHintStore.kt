package dev.tomasflopes.musicplayer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.tomasflopes.musicplayer.core.analysis.AnalysisHintStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Whether the import hint was closed, in the same Preferences DataStore as the other settings. */
class DataStoreAnalysisHintStore(
    private val store: DataStore<Preferences>,
) : AnalysisHintStore {
    override val dismissed: Flow<Boolean> = store.data.map { it[DISMISSED] ?: false }.distinctUntilChanged()

    override suspend fun dismiss() {
        store.edit { it[DISMISSED] = true }
    }

    private companion object {
        val DISMISSED = booleanPreferencesKey("analysis_hint_dismissed")
    }
}
