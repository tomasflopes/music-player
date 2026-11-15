package dev.tomasflopes.musicplayer.data.analysis

import dev.tomasflopes.musicplayer.core.analysis.AnalysisBatchRunner
import dev.tomasflopes.musicplayer.core.analysis.SoundProfiler
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.dao.AnalysisProgressRow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** What Settings shows about analysis: analysed, still waiting on the phone queue, and given up (ADR 0058, 0059). */
class RoomAnalysisProgress
    @Inject
    constructor(
        private val db: MusicDatabase,
        private val profiler: SoundProfiler,
    ) {
        fun observe(): Flow<AnalysisProgressRow> =
            db.soundProfileDao().observeProgress(profiler.version, AnalysisBatchRunner.MAX_ATTEMPTS)
    }
