package dev.tomasflopes.musicplayer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistDismissals
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistReader
import dev.tomasflopes.musicplayer.core.model.ports.SongArtistSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.taste.config.SuggestionConfig
import dev.tomasflopes.musicplayer.core.taste.score.ScoreBoard
import dev.tomasflopes.musicplayer.core.taste.suggest.AlbumLinkFilter
import dev.tomasflopes.musicplayer.core.taste.suggest.ArtistLinkFilter
import dev.tomasflopes.musicplayer.core.taste.suggest.DismissedNeighbourFilter
import dev.tomasflopes.musicplayer.core.taste.suggest.PlaylistSuggestions
import dev.tomasflopes.musicplayer.core.taste.suggest.SuggestionFilter
import dev.tomasflopes.musicplayer.core.taste.suggest.SuggestionRanker
import dev.tomasflopes.musicplayer.core.taste.suggest.TasteScoreFilter
import javax.inject.Singleton

/**
 * Playlist suggestions (ADR 0051): the config, the filters and the use case. A new filter is one more
 * `@Provides @IntoSet` line here (Guidelines 2.4); the use case and the screens never name a filter.
 */
@Module
@InstallIn(SingletonComponent::class)
object SuggestionModule {
    @Provides
    @Singleton
    fun suggestionConfig() = SuggestionConfig()

    @Provides @IntoSet
    fun artistLinkFilter(config: SuggestionConfig): SuggestionFilter = ArtistLinkFilter(config.artist)

    @Provides @IntoSet
    fun albumLinkFilter(config: SuggestionConfig): SuggestionFilter = AlbumLinkFilter(config.album)

    @Provides @IntoSet
    fun tasteScoreFilter(config: SuggestionConfig): SuggestionFilter = TasteScoreFilter(config.taste)

    @Provides @IntoSet
    fun dismissedNeighbourFilter(config: SuggestionConfig): SuggestionFilter =
        DismissedNeighbourFilter(config.dismissed)

    @Provides
    fun suggestionRanker(filters: @JvmSuppressWildcards Set<SuggestionFilter>) = SuggestionRanker(filters)

    @Provides
    fun playlistSuggestions(
        playlists: PlaylistReader,
        dismissals: PlaylistDismissals,
        library: SongReader,
        artists: SongArtistSource,
        board: ScoreBoard,
        ranker: SuggestionRanker,
        config: SuggestionConfig,
    ) = PlaylistSuggestions(playlists, dismissals, library, artists, board, ranker, config)
}
