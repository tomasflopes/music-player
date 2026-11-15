package dev.tomasflopes.musicplayer.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.analysis.BenchInput
import dev.tomasflopes.musicplayer.core.analysis.BenchReport
import dev.tomasflopes.musicplayer.core.analysis.BenchRun
import dev.tomasflopes.musicplayer.core.analysis.BenchRunner
import dev.tomasflopes.musicplayer.core.analysis.BenchSample
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import dev.tomasflopes.musicplayer.core.analysis.SampleRateCount
import dev.tomasflopes.musicplayer.core.analysis.SongTimings
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject

/** What the benchmark screen shows: whether it runs, how far it is, and the finished report. */
data class BenchUiState(
    val running: Boolean = false,
    val progress: String = "",
    val report: String = "",
)

/**
 * Debug-only (ADR 0019, 0054): times the decoder on this phone over the same fixed-seed sample as `analyser bench` on
 * the Mac. Paths are made relative to the music folder and NFC-normalised first, so on a byte-for-byte copy of the
 * library both sides pick the same songs.
 */
@HiltViewModel
class AnalysisBenchmarkViewModel
    @Inject
    constructor(
        private val source: PcmSource,
        private val songs: SongReader,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(BenchUiState())
        val state: StateFlow<BenchUiState> = mutableState.asStateFlow()

        /** Songs that threw OutOfMemoryError in the timed run; only touched from the single benchmark coroutine. */
        private var memoryFailures = 0

        fun start() {
            if (mutableState.value.running) return
            mutableState.value = BenchUiState(running = true, progress = "Reading the library")
            viewModelScope.launch(Dispatchers.Default) {
                val finished =
                    try {
                        BenchUiState(report = benchmark())
                    } catch (e: IllegalStateException) {
                        BenchUiState(progress = e.message.orEmpty())
                    }
                mutableState.value = finished
            }
        }

        private suspend fun benchmark(): String {
            val locations = locationsByRelativePath()
            check(locations.isNotEmpty()) { "The library is empty. Scan a folder first." }
            val sample = BenchSample.pick(locations.keys, SAMPLE_SONGS).map { locations.getValue(it) }
            val survey = BenchSample.pick(locations.keys, SURVEY_SONGS).map { locations.getValue(it) }

            val surveyed = survey.mapIndexedNotNull { index, location -> sampleRateOf(index, survey.size, location) }
            val rates = SampleRateCount.of(surveyed)
            val runner = BenchRunner(source)
            sample.take(WARM_UP_SONGS).forEach { measure(runner, it) }
            memoryFailures = 0

            val timings = mutableListOf<SongTimings>()
            val start = System.nanoTime()
            sample.forEachIndexed { i, location ->
                progress("Timing song ${i + 1} of ${sample.size}")
                measure(runner, location)?.let { timings += it }
            }
            val wall = System.nanoTime() - start
            check(timings.isNotEmpty()) { "No song could be decoded." }

            val run = BenchRun("this phone, 1 thread", parallelism = 1, wallNs = wall, timings = timings)
            val failed = sample.size - timings.size
            val input =
                BenchInput(
                    seed = BenchSample.DEFAULT_SEED,
                    failed = failed,
                    sampleRates = rates,
                    runs = listOf(run),
                    failedByMemory = memoryFailures,
                )
            return BenchReport.format(input)
        }

        private suspend fun locationsByRelativePath(): Map<String, String> {
            val root =
                settings.musicRoot
                    .first()
                    ?.relativePath
                    .orEmpty()
                    .trim('/')
            val prefix = if (root.isEmpty()) "" else "$root/"
            return songs
                .observeSongs()
                .first()
                .associate { normalised(it.path.removePrefix(prefix)) to it.contentUri }
        }

        private fun sampleRateOf(
            index: Int,
            total: Int,
            location: String,
        ): Int? {
            progress("Counting sample rates ${index + 1} of $total")
            return try {
                source.info(location).sampleRate
            } catch (e: PcmDecodeException) {
                null.also { e.message }
            }
        }

        private fun measure(
            runner: BenchRunner,
            location: String,
        ): SongTimings? =
            try {
                runner.measure(location)
            } catch (ignored: PcmDecodeException) {
                null
            } catch (ignored: OutOfMemoryError) {
                // Benchmark only: a song that does not fit the heap counts as failed, so the run can report how many.
                memoryFailures++
                null
            }

        private fun progress(text: String) = mutableState.update { it.copy(progress = text) }

        private fun normalised(path: String): String = Normalizer.normalize(path, Normalizer.Form.NFC)

        private companion object {
            const val SAMPLE_SONGS = 60
            const val SURVEY_SONGS = 500
            const val WARM_UP_SONGS = 3
        }
    }
