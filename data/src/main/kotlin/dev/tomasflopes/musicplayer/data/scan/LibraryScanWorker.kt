package dev.tomasflopes.musicplayer.data.scan

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.tomasflopes.musicplayer.core.analysis.AnalysisScheduler
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import kotlinx.coroutines.flow.first

/** Runs one [LibraryScanner.scan] in the background. Progress goes through [ScanProgressBus]. */
@HiltWorker
class LibraryScanWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val scanner: LibraryScanner,
        private val settings: SettingsRepository,
        private val progress: ScanProgressBus,
        private val analysis: AnalysisScheduler,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val root = settings.musicRoot.first() ?: return Result.success()
            return try {
                scanner.scan(root, progress::publish)
                // New or changed songs have no profile yet: the phone analysis picks them up (ADR 0059).
                analysis.request()
                Result.success()
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // One scan failing must not keep the app from retrying on the next trigger.
                Log.e(TAG, "library scan failed", e)
                progress.failed()
                Result.failure()
            }
        }

        companion object {
            const val UNIQUE_NAME = "library-scan"
            private const val TAG = "LibraryScan"
        }
    }
