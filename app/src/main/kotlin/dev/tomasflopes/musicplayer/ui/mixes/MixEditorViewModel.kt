package dev.tomasflopes.musicplayer.ui.mixes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SortKey
import dev.tomasflopes.musicplayer.core.model.mixes.CreateMix
import dev.tomasflopes.musicplayer.core.model.mixes.DeleteMix
import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.model.mixes.MixLimits
import dev.tomasflopes.musicplayer.core.model.mixes.MixReader
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeedKind
import dev.tomasflopes.musicplayer.core.model.mixes.UpdateMix
import dev.tomasflopes.musicplayer.core.model.ports.ArtistReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** At most this many matches of each kind are drawn, so a 16k library never makes a 16k-row list (ADR 0041). */
private const val MATCH_LIMIT = 50

/** A picked seed with the text that stands for it in the list. */
data class SeedItem(
    val seed: MixSeed,
    val label: String,
)

data class MixEditorState(
    val isNew: Boolean = true,
    val name: String = "",
    val query: String = "",
    val seeds: List<SeedItem> = emptyList(),
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val message: String? = null,
)

/**
 * The create and edit screen of a mix (design 7.6, ADR 0076): a name, 3 to 5 seeds and a search box over the songs and
 * artists of the library (a plain contains-match, as the playlist picker). The rules for names and seed counts are the
 * use cases' (`CreateMix`, `UpdateMix`); this class only holds what is on screen.
 */
@HiltViewModel
class MixEditorViewModel
    @Inject
    constructor(
        private val mixes: MixReader,
        songs: SongReader,
        artists: ArtistReader,
        private val createMix: CreateMix,
        private val updateMix: UpdateMix,
        private val deleteMix: DeleteMix,
    ) : ViewModel() {
        private var mixId: MixId? = null
        private var originalSeedCount = 0
        private val draft = MutableStateFlow(Draft())

        private val library = songs.observeSongs()
        private val artistList = artists.observeArtists()

        /** Song labels and artist labels by id, for the seeds picked so far. */
        private val labels =
            combine(library, artistList) { allSongs, allArtists ->
                allSongs.associate { it.id.value to "${it.title} - ${it.artistName}" } to
                    allArtists.associate { it.id.value to "${it.name} (artist)" }
            }

        private val matches =
            combine(library, artistList, draft.map { it.query }.distinctUntilChanged()) { allSongs, allArtists, text ->
                val needle = SortKey.of(text)
                if (needle.isEmpty()) {
                    emptyList<Song>() to emptyList()
                } else {
                    val foundSongs = allSongs.filter { songHaystack(it).contains(needle) }
                    val foundArtists = allArtists.filter { SortKey.of(it.name).contains(needle) }
                    foundSongs.take(MATCH_LIMIT) to foundArtists.take(MATCH_LIMIT)
                }
            }

        val state: StateFlow<MixEditorState> =
            combine(draft, labels, matches) { now, (songLabels, artistLabels), (foundSongs, foundArtists) ->
                MixEditorState(
                    isNew = now.isNew,
                    name = now.name,
                    query = now.query,
                    seeds =
                        now.seeds.map { seed ->
                            val known = if (seed.kind == MixSeedKind.SONG) songLabels else artistLabels
                            SeedItem(seed, known[seed.refId] ?: "Unknown ${seed.kind.wire}")
                        },
                    songs = foundSongs,
                    artists = foundArtists,
                    message = now.message,
                )
            }.stateIn(viewModelScope, WhileSubscribed, MixEditorState())

        /** Loads [id] once, or starts an empty new mix when it is `null`. */
        suspend fun open(id: Long?) {
            if (id == null || mixId != null) return
            val mix = mixes.mix(MixId(id)) ?: return
            mixId = mix.id
            originalSeedCount = mix.seeds.size
            draft.update { Draft(name = mix.name, seeds = mix.seeds, isNew = false) }
        }

        fun onNameChange(text: String) = draft.update { it.copy(name = text, message = null) }

        fun onQueryChange(text: String) = draft.update { it.copy(query = text) }

        fun toggle(seed: MixSeed) =
            draft.update { now ->
                when {
                    seed in now.seeds -> now.copy(seeds = now.seeds - seed, message = null)
                    now.seeds.size >= MixLimits.MAX_SEEDS ->
                        now.copy(message = "A mix holds at most ${MixLimits.MAX_SEEDS} seeds")
                    else -> now.copy(seeds = now.seeds + seed, message = null)
                }
            }

        /** Saves the mix; `true` when it was saved, otherwise [MixEditorState.message] says why not. */
        suspend fun save(): Boolean {
            val now = draft.value
            val id = mixId
            val failure =
                if (id == null) {
                    when (createMix(now.name, now.seeds)) {
                        is CreateMix.Result.Created -> null
                        CreateMix.Result.InvalidName -> NAME_MISSING
                        CreateMix.Result.InvalidSeeds -> INVALID_SEEDS
                    }
                } else {
                    val min = minOf(MixLimits.CREATE_MIN_SEEDS, maxOf(originalSeedCount, MixLimits.MIN_SEEDS))
                    when (updateMix(id, now.name, now.seeds, min)) {
                        UpdateMix.Result.UPDATED -> null
                        UpdateMix.Result.INVALID_NAME -> NAME_MISSING
                        UpdateMix.Result.INVALID_SEEDS -> INVALID_SEEDS
                    }
                }
            draft.update { it.copy(message = failure) }
            return failure == null
        }

        suspend fun delete() {
            mixId?.let { deleteMix(it) }
        }

        private fun songHaystack(song: Song): String =
            SortKey.of("${song.title} ${song.artistName} ${song.tagArtistName} ${song.albumTitle.orEmpty()}")

        /** What the owner has typed and picked so far. */
        private data class Draft(
            val name: String = "",
            val query: String = "",
            val seeds: List<MixSeed> = emptyList(),
            val message: String? = null,
            val isNew: Boolean = true,
        )

        private companion object {
            const val NAME_MISSING = "Give the mix a name"
            const val INVALID_SEEDS = "Pick ${MixLimits.CREATE_MIN_SEEDS} to ${MixLimits.MAX_SEEDS} songs or artists"
        }
    }
