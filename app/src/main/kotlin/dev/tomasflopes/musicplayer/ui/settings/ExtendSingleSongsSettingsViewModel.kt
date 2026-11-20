package dev.tomasflopes.musicplayer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The "Extend single songs" row of Settings (ADR 0080): on by default, saved through [SettingsRepository]. */
@HiltViewModel
class ExtendSingleSongsSettingsViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
    ) : ViewModel() {
        val enabled: StateFlow<Boolean> = settings.extendSingleSongs.stateIn(viewModelScope, WhileSubscribed, true)

        fun onEnabled(on: Boolean) {
            viewModelScope.launch { settings.setExtendSingleSongs(on) }
        }
    }
