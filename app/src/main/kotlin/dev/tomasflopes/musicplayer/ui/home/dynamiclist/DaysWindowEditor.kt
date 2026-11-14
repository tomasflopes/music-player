package dev.tomasflopes.musicplayer.ui.home.dynamiclist

import dev.tomasflopes.musicplayer.core.model.config.LibraryConfig
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Edits the Recently added window (design 7.7, ADR 0022) for the Settings screen: it checks the range and writes the
 * Settings value the list reads. It has its own [error]; [scope] is the screen's ViewModel scope, in which the write
 * runs.
 */
class DaysWindowEditor(
    private val settings: SettingsRepository,
    private val config: LibraryConfig,
    private val scope: CoroutineScope,
) {
    private val rejected = MutableStateFlow<DaysError?>(null)

    /** Why the last input was not applied, until the next valid input or [clearError]. */
    val error: StateFlow<DaysError?> = rejected

    /** A quick option or any in-range number; returns whether it was applied, so the picker can close. */
    fun applyDays(days: Int): Boolean {
        if (days !in config.recentlyAddedDaysRange) {
            rejected.value = DaysError.OUT_OF_RANGE
            return false
        }
        rejected.value = null
        scope.launch { settings.setRecentlyAddedDays(days) }
        return true
    }

    /** The custom field's text; returns whether it was applied. */
    fun applyText(text: String): Boolean {
        val days = text.trim().toIntOrNull()
        if (days == null) {
            rejected.value = DaysError.NOT_A_NUMBER
            return false
        }
        return applyDays(days)
    }

    fun clearError() {
        rejected.value = null
    }

    fun window(
        days: Int,
        error: DaysError?,
    ) = DaysWindow(days, config.recentlyAddedQuickOptions, config.recentlyAddedDaysRange, error)
}
