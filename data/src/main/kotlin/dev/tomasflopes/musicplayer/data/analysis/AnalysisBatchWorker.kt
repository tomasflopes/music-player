package dev.tomasflopes.musicplayer.data.analysis

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.tomasflopes.musicplayer.core.analysis.AnalysisBatchRunner
import dev.tomasflopes.musicplayer.core.analysis.AnalysisClock
import dev.tomasflopes.musicplayer.core.analysis.AnalysisConstraintRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * One batch of phone analysis (ADR 0059): runs [AnalysisBatchRunner] for about nine minutes, then asks for its own
 * next run while songs still wait. No foreground service and no notification; the work survives process death because
 * the queue is a query and each finished song is already stored.
 */
@HiltWorker
class AnalysisBatchWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val runner: AnalysisBatchRunner,
        private val clock: AnalysisClock,
        private val scheduler: WorkManagerAnalysisScheduler,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val deadline = clock.nowMs() + TimeUnit.MINUTES.toMillis(AnalysisConstraintRule.BATCH_MINUTES)
            return try {
                val result = withContext(Dispatchers.Default) { runner.run(deadline) }
                // A run that did nothing must not chain itself: that would spin. The next scan asks again.
                if (result.analysed + result.failed > 0) scheduler.requestAfterBatch()
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // The queue is a query, so nothing is lost; the next scan or import starts the job again.
                Log.e(TAG, "analysis batch failed", e)
                Result.failure()
            }
        }

        companion object {
            private const val TAG = "AnalysisBatch"
        }
    }
