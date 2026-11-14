package dev.tomasflopes.musicplayer.core.model.undo

import kotlinx.coroutines.flow.StateFlow

/** What the snackbar shows ([message]) and which offer it belongs to ([id]). */
data class PendingUndo(
    val id: Long,
    val message: String,
)

/**
 * The one undo the app offers (ADR 0046): the last removal, from the queue or a playlist. At most one is pending.
 * A new [offer] replaces it, [undo] runs it once, and [expire] drops it when the snackbar goes away.
 * Every call that names an [id] is ignored when that offer is no longer the pending one.
 */
interface UndoSlot {
    val pending: StateFlow<PendingUndo?>

    /** Makes [undo] the pending action, replacing any earlier one without running it. */
    fun offer(
        message: String,
        undo: suspend () -> Unit,
    ): PendingUndo

    /** Clears the slot, then runs the action. `false` when [id] is not the pending offer. */
    suspend fun undo(id: Long): Boolean

    /** Clears the slot without running the action. */
    fun expire(id: Long)
}
