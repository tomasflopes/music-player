package dev.tomasflopes.musicplayer.ui.settings.analysis

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.data.analysis.ProfileImportException
import dev.tomasflopes.musicplayer.data.analysis.ProfileImporter
import dev.tomasflopes.musicplayer.data.db.dao.AnalysisSummaryRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Analysis row shows while an import runs and after it ends. */
data class ImportState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
)

@HiltViewModel
class AnalysisImportViewModel
    @Inject
    constructor(
        private val importer: ProfileImporter,
    ) : ViewModel() {
        /** Profiled and queued songs for the current profiler version; the row reads "N matched, M queued". */
        val summary: StateFlow<AnalysisSummaryRow> =
            importer
                .observeSummary()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AnalysisSummaryRow(0, 0))

        private val mutableState = MutableStateFlow(ImportState())
        val state: StateFlow<ImportState> = mutableState.asStateFlow()

        /** Imports the export the owner picked. A second import changes nothing that is already stored. */
        fun onFilePicked(uri: Uri) {
            if (mutableState.value.running) return
            viewModelScope.launch {
                mutableState.value = ImportState(running = true)
                mutableState.value =
                    try {
                        val result =
                            importer.importFrom(uri) { fraction ->
                                mutableState.value = mutableState.value.copy(progress = fraction)
                            }
                        val retired =
                            if (result.retiredMatched > 0) " (${result.retiredMatched} retired not counted)" else ""
                        ImportState(message = "Imported: ${result.matched} matched$retired.")
                    } catch (e: ProfileImportException) {
                        ImportState(message = e.message ?: "The import failed.")
                    }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
