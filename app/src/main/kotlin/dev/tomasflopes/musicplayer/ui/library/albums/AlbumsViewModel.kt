package dev.tomasflopes.musicplayer.ui.library.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.ports.AlbumReader
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.ui.WhileSubscribed
import dev.tomasflopes.musicplayer.ui.library.IndexedList
import dev.tomasflopes.musicplayer.ui.library.indexed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Albums sub-tab of Library: albums by title, with the A-Z sections computed once per snapshot. */
@HiltViewModel
class AlbumsViewModel
    @Inject
    constructor(
        songReader: SongReader,
        albumReader: AlbumReader,
    ) : ViewModel() {
        /** `null` until the first read, so an empty library and a loading one look different. */
        val albums: StateFlow<IndexedList<AlbumListItem>?> =
            albumReader
                .observeAlbums()
                .map { songReader.withCovers(it) }
                .indexed { it.album.title }
                .stateIn(viewModelScope, WhileSubscribed, null)
    }
