package dev.tomasflopes.musicplayer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.tomasflopes.musicplayer.ui.stats.WrappedCard
import dev.tomasflopes.musicplayer.ui.stats.cards.ComebackArtistCard
import dev.tomasflopes.musicplayer.ui.stats.cards.ListeningClockCard
import dev.tomasflopes.musicplayer.ui.stats.cards.MinutesListenedCard
import dev.tomasflopes.musicplayer.ui.stats.cards.MostSkippedCard
import dev.tomasflopes.musicplayer.ui.stats.cards.NewDiscoveriesCard
import dev.tomasflopes.musicplayer.ui.stats.cards.TopAlbumsCard
import dev.tomasflopes.musicplayer.ui.stats.cards.TopArtistsCard
import dev.tomasflopes.musicplayer.ui.stats.cards.TopSongsCard

/** The Wrapped cards (ADR 0091): one `@IntoSet` line per card; the Stats tab reads the set and never names a member. */
@Module
@InstallIn(SingletonComponent::class)
abstract class StatsModule {
    @Binds @IntoSet
    abstract fun topSongs(card: TopSongsCard): WrappedCard

    @Binds @IntoSet
    abstract fun topArtists(card: TopArtistsCard): WrappedCard

    @Binds @IntoSet
    abstract fun topAlbums(card: TopAlbumsCard): WrappedCard

    @Binds @IntoSet
    abstract fun minutes(card: MinutesListenedCard): WrappedCard

    @Binds @IntoSet
    abstract fun discoveries(card: NewDiscoveriesCard): WrappedCard

    @Binds @IntoSet
    abstract fun mostSkipped(card: MostSkippedCard): WrappedCard

    @Binds @IntoSet
    abstract fun clock(card: ListeningClockCard): WrappedCard

    @Binds @IntoSet
    abstract fun comeback(card: ComebackArtistCard): WrappedCard
}
