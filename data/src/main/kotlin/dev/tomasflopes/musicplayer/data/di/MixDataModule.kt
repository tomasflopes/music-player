package dev.tomasflopes.musicplayer.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.model.mixes.MixReader
import dev.tomasflopes.musicplayer.core.model.mixes.MixStore
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.repo.RoomMixReader
import dev.tomasflopes.musicplayer.data.repo.RoomMixStore

/** The mix ports (ADR 0076), apart from [RepositoryModule] to keep that one under the function limit. */
@Module
@InstallIn(SingletonComponent::class)
object MixDataModule {
    @Provides fun mixReader(db: MusicDatabase): MixReader = RoomMixReader(db)

    @Provides fun mixStore(db: MusicDatabase): MixStore = RoomMixStore(db)
}
