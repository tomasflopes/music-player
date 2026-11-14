package dev.tomasflopes.musicplayer.core.model.ports

import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
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

    /** Crossfade on/off and its length (phase 6); off, 4 s until the owner changes it. */
    val crossfade: Flow<CrossfadeSettings>

    suspend fun setMusicRoot(root: MusicRoot)

    suspend fun setRecentlyAddedDays(days: Int)

    /** Saved with the length coerced into [CrossfadeSettings.DURATION_RANGE_MS]. */
    suspend fun setCrossfade(settings: CrossfadeSettings)
}
