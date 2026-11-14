package dev.tomasflopes.musicplayer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.artistimage.ArtistPhotoMode
import dev.tomasflopes.musicplayer.core.artistimage.ArtistPhotoSettings
import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import dev.tomasflopes.musicplayer.ui.home.dynamiclist.DaysWindowEditor
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Settings screen (design 7.0, 7.7): the music folder, the Recently added window and the artist photo setting.
 *
 * Changing the folder is not done here: the picker belongs to `MainActivity`, and what it returns goes through
 * `ShellViewModel.folderPicked`, which saves the root and requests the scan. This ViewModel only shows the saved
 * root, so the path updates once that has happened. The window is edited through a [DaysWindowEditor], which checks the
 * range and writes the value the Recently added list reads (ADR 0022).
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        settings: SettingsRepository,
        private val artistPhotos: ArtistPhotoSettings,
        config: LibraryConfig,
        settingsEntries: Set<@JvmSuppressWildcards SettingsEntry>,
    ) : ViewModel() {
        init {
            val ids = settingsEntries.map { it.id }
            require(ids.size == ids.toSet().size) { "Settings entry ids must be unique, got $ids" }
        }

        val entries: List<SettingsEntry> = settingsEntries.sortedWith(compareBy({ it.order }, { it.id }))

        private val daysEditor = DaysWindowEditor(settings, config, viewModelScope)

        val state: StateFlow<SettingsUiState> =
            combine(
                settings.musicRoot,
                settings.recentlyAddedDays,
                daysEditor.error,
                artistPhotos.mode,
            ) { root, days, error, photos ->
                SettingsUiState(root?.displayPath(), daysEditor.window(days, error), photos)
            }.stateIn(
                viewModelScope,
                WhileSubscribed,
                SettingsUiState(
                    musicFolder = null,
                    recentlyAdded = daysEditor.window(config.recentlyAddedDefaultDays, null),
                    artistPhotos = ArtistPhotoMode.DEFAULT,
                ),
            )

        /** A quick option or any in-range number; returns whether it was applied, so the picker can close. */
        fun onQuickDays(days: Int): Boolean = daysEditor.applyDays(days)

        /** The custom field's text; returns whether it was applied. */
        fun onCustomDays(text: String): Boolean = daysEditor.applyText(text)

        fun onPickerDismissed() = daysEditor.clearError()

        /** "Artist photos from the internet": Off makes no request at all (ADR 0028). */
        fun onArtistPhotoMode(mode: ArtistPhotoMode) {
            viewModelScope.launch { artistPhotos.setMode(mode) }
        }
    }
