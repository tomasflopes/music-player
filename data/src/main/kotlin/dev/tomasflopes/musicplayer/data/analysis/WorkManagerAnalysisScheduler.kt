package dev.tomasflopes.musicplayer.data.analysis

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.tomasflopes.musicplayer.core.analysis.AnalysisBatchRunner
import dev.tomasflopes.musicplayer.core.analysis.AnalysisConstraintRule
import dev.tomasflopes.musicplayer.core.analysis.AnalysisScheduler
import dev.tomasflopes.musicplayer.core.analysis.SoundProfiler
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One unique WorkManager job, `sound-profile-batch` (ADR 0059). [request] is the entry after a scan or an import: it
 * starts the job when songs wait, replacing one that is only waiting (so the constraint follows the current queue) and
 * leaving one that runs alone. [requestAfterBatch] is the job asking for its own next run.
 */
@Singleton
class WorkManagerAnalysisScheduler
    @Inject
    constructor(
        private val workManager: WorkManager,
        private val db: MusicDatabase,
        private val profiler: SoundProfiler,
    ) : AnalysisScheduler {
        override suspend fun request() {
            val waiting = waiting()
            if (waiting == 0) return
            val infos = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME).first()
            val running = infos.any { it.state == WorkInfo.State.RUNNING }
            enqueue(if (running) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, waiting)
        }

        /** Chains the next batch after the one that is running now, when songs still wait. */
        suspend fun requestAfterBatch() {
            val waiting = waiting()
            if (waiting > 0) enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE, waiting)
        }

        private suspend fun waiting(): Int =
            db.soundProfileDao().countWaiting(profiler.version, AnalysisBatchRunner.MAX_ATTEMPTS)

        private fun enqueue(
            policy: ExistingWorkPolicy,
            waiting: Int,
        ) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiresCharging(AnalysisConstraintRule.requiresCharging(waiting))
                    .build()
            val work = OneTimeWorkRequestBuilder<AnalysisBatchWorker>().setConstraints(constraints).build()
            workManager.enqueueUniqueWork(UNIQUE_NAME, policy, work)
        }

        companion object {
            const val UNIQUE_NAME = "sound-profile-batch"
        }
    }
