package dev.tomasflopes.musicplayer.ui.settings

import dev.tomasflopes.musicplayer.core.artistimage.ArtistPhotoMode
import dev.tomasflopes.musicplayer.ui.home.dynamiclist.DaysWindow

/**
 * [musicFolder] is `null` until a folder is saved; [recentlyAdded] is the window the Recently added list shows;
 * [artistPhotos] is whether and when artist photos may be downloaded (ADR 0028).
 */
data class SettingsUiState(
    val musicFolder: String?,
    val recentlyAdded: DaysWindow,
    val artistPhotos: ArtistPhotoMode,
)
