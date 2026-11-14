package dev.tomasflopes.musicplayer.ui.library.artist.photo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageCandidates
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageEditor
import dev.tomasflopes.musicplayer.core.artistimage.CandidateResult
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.ui.art.GalleryImageStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The online photos the sheet lists: nothing asked yet, being asked, or the answer. */
sealed interface OnlinePhotos {
    data object Idle : OnlinePhotos

    data object Loading : OnlinePhotos

    data class Loaded(
        val result: CandidateResult,
    ) : OnlinePhotos
}

/**
 * "Change photo" on the artist page (design 7.1, ADR 0029): the four ways to set an artist's picture. An edit
 * finishes even if the page is left right after it, so a gallery copy is never left half done.
 */
@HiltViewModel
class ArtistPhotoViewModel
    @Inject
    constructor(
        private val candidates: ArtistImageCandidates,
        private val editor: ArtistImageEditor,
        private val gallery: GalleryImageStore,
    ) : ViewModel() {
        private val _online = MutableStateFlow<OnlinePhotos>(OnlinePhotos.Idle)
        val online: StateFlow<OnlinePhotos> = _online.asStateFlow()

        /** Asks Deezer for the other artists with this name; one request at a time. */
        fun loadOnline(name: String) {
            if (_online.value == OnlinePhotos.Loading) return
            _online.value = OnlinePhotos.Loading
            viewModelScope.launch { _online.value = OnlinePhotos.Loaded(candidates.find(name)) }
        }

        fun clearOnline() {
            _online.value = OnlinePhotos.Idle
        }

        fun useOnline(
            id: ArtistId,
            url: String,
        ) = edit { editor.useRemote(id, url) }

        fun useGallery(
            id: ArtistId,
            picked: Uri,
        ) = edit { gallery.copy(id, picked)?.let { editor.useLocalFile(id, it) } }

        fun useGenerated(id: ArtistId) = edit { editor.useGenerated(id) }

        fun useAutomatic(id: ArtistId) = edit { editor.reset(id) }

        private fun edit(block: suspend () -> Unit) {
            viewModelScope.launch { withContext(NonCancellable) { block() } }
        }
    }
