package dev.tomasflopes.musicplayer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.playlists.AddAlbumToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.AddArtistToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.AddSongsToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.CreatePlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistStore
import dev.tomasflopes.musicplayer.core.model.playlists.RenamePlaylist
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import java.time.Clock

/** The playlist use cases are plain classes in `:core:model`; the annotations live here (plan D5). */
@Module
@InstallIn(SingletonComponent::class)
object PlaylistModule {
    @Provides fun createPlaylist(
        store: PlaylistStore,
        clock: Clock,
    ) = CreatePlaylist(store, clock)

    @Provides fun renamePlaylist(
        store: PlaylistStore,
        clock: Clock,
    ) = RenamePlaylist(store, clock)

    @Provides fun addSongs(
        store: PlaylistStore,
        clock: Clock,
    ) = AddSongsToPlaylist(store, clock)

    @Provides fun addAlbum(
        songs: SongReader,
        add: AddSongsToPlaylist,
    ) = AddAlbumToPlaylist(songs, add)

    @Provides fun addArtist(
        songs: SongReader,
        add: AddSongsToPlaylist,
    ) = AddArtistToPlaylist(songs, add)
}
