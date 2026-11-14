package dev.tomasflopes.musicplayer.core.artistimage

import kotlinx.coroutines.flow.first

/** The setting decides, and the connection is read only when the setting is not Off. */
class ModeOnlinePolicy(
    private val settings: ArtistPhotoSettings,
    private val network: () -> NetworkKind,
) : OnlinePolicy {
    override suspend fun allowed(): Boolean {
        val mode = settings.mode.first()
        return mode != ArtistPhotoMode.OFF && mode.allowsRequest(network())
    }
}
