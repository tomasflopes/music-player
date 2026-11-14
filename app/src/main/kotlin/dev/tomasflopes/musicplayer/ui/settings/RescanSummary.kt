package dev.tomasflopes.musicplayer.ui.settings

import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import dev.tomasflopes.musicplayer.ui.home.label

internal const val RESCAN_IDLE = "Look for new, changed and removed songs"

/** Under the "Rescan library" title: the scan's progress while one runs or has failed, else what the row does. */
internal fun rescanSummary(scan: ScanProgress): String = scan.label() ?: RESCAN_IDLE
