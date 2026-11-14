package dev.tomasflopes.musicplayer.core.model.undo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class SingleUndoSlot : UndoSlot {
    private val nextId = AtomicLong(1)
    private var held: Pair<PendingUndo, suspend () -> Unit>? = null
    private val _pending = MutableStateFlow<PendingUndo?>(null)
    override val pending: StateFlow<PendingUndo?> = _pending.asStateFlow()

    override fun offer(
        message: String,
        undo: suspend () -> Unit,
    ): PendingUndo {
        val offer = PendingUndo(nextId.getAndIncrement(), message)
        synchronized(this) {
            held = offer to undo
            _pending.value = offer
        }
        return offer
    }

    override suspend fun undo(id: Long): Boolean {
        val run = take(id) ?: return false
        run()
        return true
    }

    override fun expire(id: Long) {
        take(id)
    }

    private fun take(id: Long): (suspend () -> Unit)? =
        synchronized(this) {
            val current = held
            if (current == null || current.first.id != id) return null
            held = null
            _pending.value = null
            current.second
        }
}
