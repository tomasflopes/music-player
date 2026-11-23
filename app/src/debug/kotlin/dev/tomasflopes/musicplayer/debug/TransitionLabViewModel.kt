package dev.tomasflopes.musicplayer.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.Transition
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionCause
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.core.model.ports.SongFeatureReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.transition.BeatGrid
import dev.tomasflopes.musicplayer.core.model.transition.TempoMatcher
import dev.tomasflopes.musicplayer.core.model.transition.TempoReliability
import dev.tomasflopes.musicplayer.core.model.transition.TransitionConfig
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlan
import dev.tomasflopes.musicplayer.core.model.transition.TransitionPlanner
import dev.tomasflopes.musicplayer.core.model.transition.TransitionProfile
import dev.tomasflopes.musicplayer.core.model.transition.TransitionProfileSource
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.playback.lab.LabSetup
import dev.tomasflopes.musicplayer.playback.lab.LabState
import dev.tomasflopes.musicplayer.playback.lab.TransitionLab
import dev.tomasflopes.musicplayer.ui.search.LibrarySnapshot
import dev.tomasflopes.musicplayer.ui.search.SearchEngine
import dev.tomasflopes.musicplayer.ui.search.SearchEngineFactory
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

enum class LabSlot { OUTGOING, INCOMING }

data class TransitionLabUiState(
    val query: String = "",
    val matches: List<Song> = emptyList(),
    /** The slot the next tapped song goes into. */
    val picking: LabSlot = LabSlot.OUTGOING,
    val outgoing: Song? = null,
    val incoming: Song? = null,
    /** The plan, as the planner makes it for these two songs in the DJ mix style. */
    val planLines: List<String> = emptyList(),
    /** True when the plan is a beat-matched overlap, so there is something to play. */
    val canPlay: Boolean = false,
    /** The lab's phase lock switch (ADR 0096): off by default, applies to the next run. */
    val phaseLock: Boolean = false,
    /** The lab's bass swap switch (slice 3, ADR 0100): on by default, applies to the next run. */
    val bassSwap: Boolean = true,
)

/**
 * Debug-only (ADR 0019, 0093): pick two songs, or take the current and the next one, see the plan the planner makes
 * for them, and play it in the lab's own players with a live beat-phase reading. Follows Sound neighbours (ADR 0073).
 */
@Suppress("LongParameterList")
@HiltViewModel
class TransitionLabViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val engineFactory: SearchEngineFactory,
        private val songReader: SongReader,
        private val artistReader: ArtistReader,
        private val albumReader: AlbumReader,
        private val featureReader: SongFeatureReader,
        private val playback: PlaybackController,
        private val profiles: TransitionProfileSource,
        private val planner: TransitionPlanner,
        private val config: TransitionConfig,
        private val reliability: TempoReliability,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(TransitionLabUiState())
        val state: StateFlow<TransitionLabUiState> = mutableState.asStateFlow()
        private val lab = TransitionLab(context, viewModelScope, config)

        /** What the lab is doing: readings, the summary the owner reports and the speed log. */
        val labState: StateFlow<LabState> = lab.state

        private var setup: LabSetup? = null
        private var searchJob: Job? = null

        private val engine: Deferred<SearchEngine> =
            viewModelScope.async(start = CoroutineStart.LAZY) {
                val songs = songReader.observeSongs().first()
                val features = featureReader.all()
                val artists = artistReader.observeArtists().first()
                val albums = albumReader.observeAlbums().first()
                engineFactory.build(LibrarySnapshot(artists, albums, songs, features))
            }

        fun onQueryChange(text: String) {
            mutableState.value = mutableState.value.copy(query = text, matches = emptyList())
            searchJob?.cancel()
            if (text.isBlank()) return
            searchJob =
                viewModelScope.launch {
                    val songs =
                        engine
                            .await()
                            .search(text)
                            .songs
                            .map { it.song }
                            .take(MAX_MATCHES)
                    mutableState.value = mutableState.value.copy(matches = songs)
                }
        }

        fun onSlot(slot: LabSlot) {
            mutableState.value = mutableState.value.copy(picking = slot)
        }

        fun onSongPicked(song: Song) {
            val current = mutableState.value
            val next =
                when (current.picking) {
                    LabSlot.OUTGOING ->
                        current.copy(outgoing = song, picking = LabSlot.INCOMING, matches = emptyList())
                    LabSlot.INCOMING -> current.copy(incoming = song, matches = emptyList())
                }
            mutableState.value = next
            replan(next.outgoing, next.incoming)
        }

        /** The song that is playing and the one after it in the queue. */
        fun onCurrentAndNext() {
            val playing = playback.state.value
            val current = playing.current ?: return
            val next = playing.upcoming.firstOrNull() ?: return
            mutableState.value = mutableState.value.copy(outgoing = current, incoming = next, matches = emptyList())
            replan(current, next)
        }

        fun onPlay() {
            setup?.let(lab::startTransition)
        }

        fun onDriftTest() {
            setup?.let(lab::startDrift)
        }

        fun onSpeedChange() = lab.changeSpeed()

        fun onBassSwap(enabled: Boolean) {
            lab.bassSwap = enabled
            mutableState.value = mutableState.value.copy(bassSwap = enabled)
        }

        fun onPhaseLock(enabled: Boolean) {
            lab.phaseLock = enabled
            mutableState.value = mutableState.value.copy(phaseLock = enabled)
        }

        fun onStop() = lab.stop()

        override fun onCleared() = lab.stop()

        private fun replan(
            outgoing: Song?,
            incoming: Song?,
        ) {
            setup = null
            if (outgoing == null || incoming == null) return
            viewModelScope.launch {
                val outProfile = profiles.profile(outgoing.id.value)
                val inProfile = profiles.profile(incoming.id.value)
                val settings = CrossfadeSettings(enabled = true, style = TransitionStyle.DJ_MIX)
                val transition =
                    Transition(outgoing, incoming, TransitionCause.END_OF_SONG, shuffled = true, repeatOne = false)
                val plan = planner.plan(settings, transition, outProfile, inProfile)
                if (plan is TransitionPlan.Overlap && outProfile != null && inProfile != null) {
                    val outGrid = plan.outgoingGrid ?: BeatGrid.of(outProfile, config)
                    val inGrid = plan.incomingGrid ?: BeatGrid.of(inProfile, config)
                    setup = LabSetup(outgoing, incoming, plan, outGrid, inGrid)
                }
                mutableState.value =
                    mutableState.value.copy(planLines = describe(plan, outProfile, inProfile), canPlay = setup != null)
            }
        }

        private fun describe(
            plan: TransitionPlan,
            outgoing: TransitionProfile?,
            incoming: TransitionProfile?,
        ): List<String> =
            listOf("outgoing  ${profileLine(outgoing)}", "incoming  ${profileLine(incoming)}") +
                when (plan) {
                    is TransitionPlan.Overlap -> overlapLines(plan)
                    is TransitionPlan.Fade -> listOf("plan: ${plan.technique} (${why(outgoing, incoming)})")
                    TransitionPlan.HardCut -> listOf("plan: hard cut")
                }

        private fun overlapLines(plan: TransitionPlan.Overlap) =
            listOf(
                "plan: ${plan.technique}",
                "ratio ${fmt(
                    plan.ratio,
                    4,
                )} (${fmt(abs(plan.ratio - 1) * PERCENT, 2)} %), speed ${plan.incomingSpeed}, " +
                    "grid ${plan.incomingGrid?.let { fmt(it.tempoBpm, 1) } ?: "-"} BPM",
                "ease-back ${plan.easeBack.size} step(s), last at ${plan.easeBack.lastOrNull()?.atMs ?: "-"} ms",
                "switch point ${plan.switchPointMs} ms of the outgoing song, lab starts ${config.labLeadMs} ms before",
                "incoming starts at ${plan.incomingStartMs} ms, overlap ${plan.overlapMs} ms",
                plan.bass?.let { "bass swap at ${it.swapStartMs} ms of the overlap over ${it.swapMs} ms, cut ${it.cutDb} dB" }
                    ?: "no bass swap",
            )

        private fun profileLine(profile: TransitionProfile?) =
            if (profile == null) {
                "no usable profile"
            } else {
                "${fmt(profile.tempoBpm, 1)} BPM, conf ${fmt(profile.tempoConfidence.toDouble(), 2)}, " +
                    "first beat ${fmt(profile.firstBeatMs, 0)} ms, music ${profile.musicStartMs}.." +
                    "${profile.musicEndMs}, intro end ${profile.introEndMs}, outro ${profile.outroStartMs}"
            }

        private fun why(
            outgoing: TransitionProfile?,
            incoming: TransitionProfile?,
        ): String =
            when {
                outgoing == null || incoming == null -> "a profile is missing"
                !reliability.isReliable(outgoing, config) || !reliability.isReliable(incoming, config) ->
                    "tempo confidence is low"
                TempoMatcher.best(outgoing.tempoBpm, incoming.tempoBpm, config.maxStretch) == null ->
                    "tempos more than ${fmt(config.maxStretch * PERCENT, 0)} % apart, even at half or double"
                else -> "the songs are too short for four bars"
            }

        private fun fmt(
            value: Double,
            digits: Int,
        ) = String.format(Locale.ROOT, "%.${digits}f", value)

        private companion object {
            const val MAX_MATCHES = 30
            const val PERCENT = 100.0
        }
    }
