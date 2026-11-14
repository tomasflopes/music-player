package dev.tomasflopes.musicplayer.ui.settings

import dev.tomasflopes.musicplayer.ui.home.dynamiclist.DaysWindow

/** [musicFolder] is `null` until a folder is saved; [recentlyAdded] is the window the Recently added list shows. */
data class SettingsUiState(
    val musicFolder: String?,
    val recentlyAdded: DaysWindow,
)
