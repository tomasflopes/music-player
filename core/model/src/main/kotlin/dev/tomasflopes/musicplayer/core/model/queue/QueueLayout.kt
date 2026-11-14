package dev.tomasflopes.musicplayer.core.model.queue

import dev.tomasflopes.musicplayer.core.model.SongId

/**
 * One row of the queue screen. [index] is its place in the whole queue (what a remove or a jump is addressed
 * with), or -1 for a row that only appears because repeat ALL wraps the queue round.
 */
data class QueueSlot(
    val index: Int,
    val songId: SongId,
    val manual: Boolean,
)

/**
 * The queue screen in screen order: [played] (oldest to newest), [current], [upcoming]. Under shuffle the queue is
 * already in the order it plays, so this is the order actually played, not the original one.
 */
data class QueueLayout(
    val played: List<QueueSlot>,
    val current: QueueSlot?,
    val upcoming: List<QueueSlot>,
) {
    /** How many of the first [upcoming] rows were queued by hand; manual rows are a leading run (ADR 0010). */
    val manualUpcoming: Int get() = upcoming.takeWhile { it.manual }.size
}

/**
 * Splits [window] (consecutive slots of the queue, in queue order) around [currentIndex]. A slot whose song is not
 * [known] (it left the library) is dropped; the others keep their own [QueueSlot.index], so addressing never shifts.
 * Wrapped rows (index -1) come after the current one, so they are upcoming.
 */
fun queueLayoutOf(
    window: List<QueueSlot>,
    currentIndex: Int,
    known: (SongId) -> Boolean,
): QueueLayout {
    val shown = window.filter { known(it.songId) }
    return QueueLayout(
        played = shown.filter { it.index in 0 until currentIndex },
        current = shown.firstOrNull { it.index == currentIndex },
        upcoming = shown.filter { it.index < 0 || it.index > currentIndex },
    )
}
