package dev.tomasflopes.musicplayer.data.scan

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.tomasflopes.musicplayer.core.model.ports.LibraryScanController
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import kotlinx.coroutines.flow.StateFlow

/** Scans run as the unique work `library-scan`; a request while one runs is dropped (policy KEEP). */
class WorkManagerScanController(
    private val workManager: WorkManager,
    private val bus: ScanProgressBus,
) : LibraryScanController {
    override val progress: StateFlow<ScanProgress> = bus.progress

    override fun requestScan() {
        workManager.enqueueUniqueWork(
            LibraryScanWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LibraryScanWorker>().build(),
        )
    }
}
