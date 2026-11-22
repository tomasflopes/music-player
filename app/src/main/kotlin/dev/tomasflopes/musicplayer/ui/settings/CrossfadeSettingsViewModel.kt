package dev.tomasflopes.musicplayer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The crossfade row of Settings (phase 6): the switch and the length, saved through [SettingsRepository]. */
@HiltViewModel
class CrossfadeSettingsViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
    ) : ViewModel() {
        val state: StateFlow<CrossfadeSettings> =
            settings.crossfade.stateIn(viewModelScope, WhileSubscribed, CrossfadeSettings())

        fun onEnabled(enabled: Boolean) = change { it.copy(enabled = enabled) }

        /** The slider was released on [seconds]; the store keeps it inside one to twelve. */
        fun onDurationSeconds(seconds: Int) = change { it.copy(durationMs = seconds * MS_PER_SECOND) }

        /** Crossfade (phase 6, the default) or DJ mix (phase 7); the length applies to both. */
        fun onStyle(style: TransitionStyle) = change { it.copy(style = style) }

        private fun change(edit: (CrossfadeSettings) -> CrossfadeSettings) {
            viewModelScope.launch { settings.setCrossfade(edit(settings.crossfade.first())) }
        }

        private companion object {
            const val MS_PER_SECOND = 1_000L
        }
    }
