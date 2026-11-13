package dev.tomasflopes.musicplayer.data.scan

import dev.tomasflopes.musicplayer.core.model.ports.ScanPhase
import dev.tomasflopes.musicplayer.core.model.ports.ScanProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The worker writes here; the controller exposes it. Singleton so both see the same scan. */
@Singleton
class ScanProgressBus
    @Inject
    constructor() {
        private val state = MutableStateFlow(ScanProgress(ScanPhase.IDLE, 0, 0))
        val progress: StateFlow<ScanProgress> = state

        fun publish(progress: ScanProgress) {
            state.value = progress
        }

        fun failed() {
            state.value = ScanProgress(ScanPhase.FAILED, 0, 0)
        }
    }
