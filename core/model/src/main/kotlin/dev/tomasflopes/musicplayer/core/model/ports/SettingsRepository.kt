package dev.tomasflopes.musicplayer.core.model.ports

import kotlinx.coroutines.flow.Flow

/** The music folder the owner picked: the picker's tree URI and where MediaStore lists it (ADR 0005). */
data class MusicRoot(
    val treeUri: String,
    val volumeName: String,
    val relativePath: String,
)

interface SettingsRepository {
    /** `null` until onboarding has picked a folder. */
    val musicRoot: Flow<MusicRoot?>
    val recentlyAddedDays: Flow<Int>

    suspend fun setMusicRoot(root: MusicRoot)

    suspend fun setRecentlyAddedDays(days: Int)
}
