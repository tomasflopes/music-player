package dev.tomasflopes.musicplayer.core.model.ports

import kotlinx.coroutines.flow.StateFlow

enum class ScanPhase { IDLE, LISTING, READING_TAGS, INDEXING, DONE, FAILED }

data class ScanProgress(
    val phase: ScanPhase,
    val done: Int,
    val total: Int,
)

interface LibraryScanController {
    val progress: StateFlow<ScanProgress>

    /** Starts a scan unless one is already running. */
    fun requestScan()
}
