package dev.tomasflopes.musicplayer.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tomasflopes.musicplayer.core.model.undo.PendingUndo
import dev.tomasflopes.musicplayer.core.model.undo.UndoSlot
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The snackbar's view of the shared [UndoSlot]. */
@HiltViewModel
class UndoViewModel
    @Inject
    constructor(
        private val slot: UndoSlot,
    ) : ViewModel() {
        val pending: StateFlow<PendingUndo?> = slot.pending

        fun undo(id: Long) {
            viewModelScope.launch { slot.undo(id) }
        }

        fun expire(id: Long) = slot.expire(id)
    }
