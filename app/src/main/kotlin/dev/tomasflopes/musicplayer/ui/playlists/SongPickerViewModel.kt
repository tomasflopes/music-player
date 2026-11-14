package dev.tomasflopes.musicplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SortKey
import dev.tomasflopes.musicplayer.core.model.playlists.AddSongsToPlaylist
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId
import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** At most this many matches are drawn, so a 16k library never makes a 16k-row list (ADR 0041). */
internal const val PICKER_LIMIT = 200

data class SongPickerState(
    val playlistName: String = "",
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val selected: Set<SongId> = emptySet(),
    val alreadyIn: Set<SongId> = emptySet(),
)

/**
 * "Add songs" to one playlist, the way into the first songs of a new playlist (design 7.12): a search box over the
 * library and ticks. A plain contains-match on title, artist and album (the search engine itself is out of scope).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SongPickerViewModel
    @Inject
    constructor(
        private val playlists: PlaylistReader,
        private val library: SongReader,
        private val addSongs: AddSongsToPlaylist,
    ) : ViewModel() {
        private val playlistId = MutableStateFlow<PlaylistId?>(null)
        private val query = MutableStateFlow("")
        private val selected = MutableStateFlow<Set<SongId>>(emptySet())

        /** Each song with the text a query is matched against, built once per library snapshot, not per keystroke. */
        private val index: Flow<List<Pair<Song, String>>> =
            library
                .observeSongs()
                .map { songs -> songs.map { it to haystack(it) } }
                .shareIn(viewModelScope, WhileSubscribed, replay = 1)

        val state: StateFlow<SongPickerState> =
            playlistId
                .flatMapLatest { id -> if (id == null) flowOf(SongPickerState()) else render(id) }
                .stateIn(viewModelScope, WhileSubscribed, SongPickerState())

        fun open(id: PlaylistId) {
            playlistId.value = id
        }

        fun onQueryChange(text: String) {
            query.value = text
        }

        fun onToggle(id: SongId) {
            selected.value = if (id in selected.value) selected.value - id else selected.value + id
        }

        /** Adds the ticked songs in library order and clears the ticks; `null` when nothing is ticked. */
        suspend fun addSelected(): String? {
            val id = playlistId.value
            val ticked = selected.value
            if (id == null || ticked.isEmpty()) return null
            val ordered =
                index
                    .first()
                    .map { it.first }
                    .filter { it.id in ticked }
                    .map { it.id }
            val result = addSongs(id, ordered)
            selected.value = emptySet()
            return AddMessage.of(
                result,
                playlists
                    .observePlaylist(id)
                    .first()
                    ?.name
                    .orEmpty(),
            )
        }

        private fun render(id: PlaylistId): Flow<SongPickerState> =
            combine(
                playlists.observePlaylist(id),
                playlists.observeSongs(id),
                index,
                query,
                selected,
            ) { playlist, inside, all, text, ticked ->
                val insideIds = inside.map { it.id }.toSet()
                SongPickerState(
                    playlistName = playlist?.name.orEmpty(),
                    query = text,
                    songs = all.matching(text),
                    selected = ticked - insideIds,
                    alreadyIn = insideIds,
                )
            }

        private fun List<Pair<Song, String>>.matching(text: String): List<Song> {
            val needle = SortKey.of(text)
            if (needle.isEmpty()) return take(PICKER_LIMIT).map { it.first }
            return asSequence()
                .filter { it.second.contains(needle) }
                .take(PICKER_LIMIT)
                .map { it.first }
                .toList()
        }

        private fun haystack(song: Song): String =
            SortKey.of(
                listOfNotNull(song.title, song.artistName, song.tagArtistName, song.albumTitle).joinToString(" "),
            )
    }
