package dev.tomasflopes.musicplayer.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tomasflopes.musicplayer.core.analysis.AnalysisBatchRunner
import dev.tomasflopes.musicplayer.core.analysis.AnalysisClock
import dev.tomasflopes.musicplayer.core.analysis.AnalysisHintStore
import dev.tomasflopes.musicplayer.core.analysis.AnalysisQueue
import dev.tomasflopes.musicplayer.core.analysis.AnalysisScheduler
import dev.tomasflopes.musicplayer.core.analysis.AnalysisStore
import dev.tomasflopes.musicplayer.core.analysis.HandBuiltProfilerV1
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import dev.tomasflopes.musicplayer.core.analysis.SoundProfiler
import dev.tomasflopes.musicplayer.data.analysis.MediaCodecPcmSource
import dev.tomasflopes.musicplayer.data.analysis.RoomAnalysisQueue
import dev.tomasflopes.musicplayer.data.analysis.RoomAnalysisStore
import dev.tomasflopes.musicplayer.data.analysis.WorkManagerAnalysisScheduler
import dev.tomasflopes.musicplayer.data.settings.DataStoreAnalysisHintStore
import javax.inject.Singleton

/** The phone's side of the `:core:analysis` ports: decoder (ADR 0053), profiler, queue, store, scheduler (ADR 0059). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalysisDataModule {
    @Binds
    abstract fun pcmSource(impl: MediaCodecPcmSource): PcmSource

    @Binds
    abstract fun queue(impl: RoomAnalysisQueue): AnalysisQueue

    @Binds
    abstract fun store(impl: RoomAnalysisStore): AnalysisStore

    @Binds
    abstract fun scheduler(impl: WorkManagerAnalysisScheduler): AnalysisScheduler

    companion object {
        @Provides
        @Singleton
        fun profiler(): SoundProfiler = HandBuiltProfilerV1()

        @Provides
        @Singleton
        fun hintStore(store: DataStore<Preferences>): AnalysisHintStore = DataStoreAnalysisHintStore(store)

        @Provides
        fun clock(): AnalysisClock = AnalysisClock { System.currentTimeMillis() }

        @Provides
        fun runner(
            source: PcmSource,
            profiler: SoundProfiler,
            queue: AnalysisQueue,
            store: AnalysisStore,
            clock: AnalysisClock,
        ): AnalysisBatchRunner = AnalysisBatchRunner(source, profiler, queue, store, clock)

        @Provides
        fun workManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)
    }
}
