package dev.tomasflopes.musicplayer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.taste.lists.RecentlyAddedProvider
import dev.tomasflopes.musicplayer.core.taste.lists.TopHundredProvider
import dev.tomasflopes.musicplayer.ui.home.HomeSection
import dev.tomasflopes.musicplayer.ui.home.sections.DynamicListsSection
import dev.tomasflopes.musicplayer.ui.home.sections.PlaylistsSection
import dev.tomasflopes.musicplayer.ui.home.sections.ShuffleAllSection
import dev.tomasflopes.musicplayer.ui.settings.SettingsEntry

/**
 * Where the extension points get their implementations: one `@IntoSet` line per new Home section or dynamic list
 * (core classes stay free of annotations, plan D5). Home and its cards read these sets and never name a member.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExtensionPointsModule {
    @Binds
    @IntoSet
    abstract fun shuffleAllSection(section: ShuffleAllSection): HomeSection

    @Binds
    @IntoSet
    abstract fun dynamicListsSection(section: DynamicListsSection): HomeSection

    @Binds
    @IntoSet
    abstract fun playlistsSection(section: PlaylistsSection): HomeSection

    /** Empty in release; the debug source set adds its link with an `@IntoSet` binding (ADR 0019). */
    @Multibinds
    abstract fun settingsEntries(): Set<SettingsEntry>

    @Binds
    @IntoSet
    abstract fun recentlyAdded(provider: RecentlyAddedProvider): DynamicListProvider

    @Binds
    @IntoSet
    abstract fun topHundred(provider: TopHundredProvider): DynamicListProvider
}
