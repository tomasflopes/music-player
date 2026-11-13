package dev.tomasflopes.musicplayer.debug

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.tomasflopes.musicplayer.ui.settings.SettingsEntry

/** Debug builds only: puts [DebugSettingsEntry] in the set Settings draws (ADR 0019). Release has no such module. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugModule {
    @Binds
    @IntoSet
    abstract fun debugSettingsEntry(entry: DebugSettingsEntry): SettingsEntry
}
