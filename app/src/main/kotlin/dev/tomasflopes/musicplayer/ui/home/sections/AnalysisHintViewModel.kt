package dev.tomasflopes.musicplayer.ui.home.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.analysis.AnalysisHintRule
import dev.tomasflopes.musicplayer.core.analysis.AnalysisHintStore
import dev.tomasflopes.musicplayer.data.analysis.RoomAnalysisProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether Home shows the import hint, from the analysis progress and whether it was closed. */
@HiltViewModel
class AnalysisHintViewModel
    @Inject
    constructor(
        progress: RoomAnalysisProgress,
        private val store: AnalysisHintStore,
    ) : ViewModel() {
        val show: StateFlow<Boolean> =
            combine(progress.observe(), store.dismissed) { row, dismissed ->
                AnalysisHintRule.shouldShow(row.profiled, row.waiting, dismissed)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        fun dismiss() {
            viewModelScope.launch { store.dismiss() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
