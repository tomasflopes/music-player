package dev.tomasflopes.musicplayer.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.analysis.ConsistencyReport
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import dev.tomasflopes.musicplayer.core.analysis.SoundProfile
import dev.tomasflopes.musicplayer.core.analysis.SoundProfiler
import dev.tomasflopes.musicplayer.core.analysis.analyseStream
import dev.tomasflopes.musicplayer.data.analysis.ImportedProfileSampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the consistency screen shows: whether it runs, how far it is, and the finished report. */
data class ConsistencyUiState(
    val running: Boolean = false,
    val progress: String = "",
    val report: String = "",
)

/**
 * Debug-only (ADR 0019, 0060): re-analyses 20 songs whose profile came from the Mac import, on this phone, and shows
 * the largest gap per v1 feature. The generated test files cover the decoders; this covers real music.
 */
@HiltViewModel
class ConsistencyReportViewModel
    @Inject
    constructor(
        private val sampler: ImportedProfileSampler,
        private val source: PcmSource,
        private val profiler: SoundProfiler,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ConsistencyUiState())
        val state: StateFlow<ConsistencyUiState> = mutableState.asStateFlow()

        fun start() {
            if (mutableState.value.running) return
            mutableState.value = ConsistencyUiState(running = true, progress = "Picking imported songs")
            viewModelScope.launch(Dispatchers.Default) { mutableState.value = ConsistencyUiState(report = compare()) }
        }

        private suspend fun compare(): String {
            val rows = sampler.sample(SAMPLE_SONGS)
            if (rows.isEmpty()) return "No imported songs. Import the Mac export in Settings, Analysis, first."
            val pairs = mutableListOf<Pair<SoundProfile, SoundProfile>>()
            var failed = 0
            rows.forEachIndexed { index, row ->
                progress("Re-analysing song ${index + 1} of ${rows.size}")
                val fresh =
                    try {
                        profiler.analyseStream(source, row.location)
                    } catch (ignored: PcmDecodeException) {
                        null
                    }
                if (fresh == null) failed++ else pairs += row.stored to fresh
            }
            val text = ConsistencyReport.format(ConsistencyReport.compare(pairs), pairs.size)
            return if (failed == 0) text else "$text\n$failed songs could not be decoded."
        }

        private fun progress(text: String) = mutableState.update { it.copy(progress = text) }

        private companion object {
            const val SAMPLE_SONGS = 20
        }
    }
