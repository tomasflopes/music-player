package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.playback.PlaybackConfig

/**
 * Keeps the player's item list equal to a window of the [PlayQueue] (plan C3): [PlaybackConfig.windowBehind]
 * songs before the current one and [PlaybackConfig.windowAhead] after it. The queue owns the order; the player
 * only ever receives the small edits that bring its window back in line, so playback is never restarted by a
 * shuffle toggle or by the window sliding along.
 */
class QueueWindowSync(
    private val queue: PlayQueue,
    private val player: PlayerItems,
    private val config: PlaybackConfig,
) {
    /** The few operations on the player's item list that the sync needs; ExoPlayer plugs in behind it. */
    interface PlayerItems {
        val count: Int
        val index: Int

        fun setAll(
            refs: List<SongRef>,
            startIndex: Int,
        )

        fun append(refs: List<SongRef>)

        fun removeRange(
            from: Int,
            to: Int,
        )

        fun removeFirst(count: Int)

        /** Puts [refs] before everything the player holds; the current item keeps playing. */
        fun prepend(refs: List<SongRef>)
    }

    /** What the player holds, by song; the player cannot be asked for refs, only for counts. */
    private val held = mutableListOf<SongRef>()

    /** Loads the whole window; this restarts the current song. */
    fun fill() {
        val window = queue.window(config.windowBehind, config.windowAhead).map { it.ref }
        held.clear()
        held += window
        player.setAll(window, currentPosition(window.size))
    }

    /** A new play request: everything is replaced. */
    fun onQueueReplaced() = fill()

    /** The player moved to its next or previous item by itself: slide the window along. */
    fun onPlayerAdvanced() {
        val trim = player.index - config.windowBehind
        if (trim > 0) {
            player.removeFirst(trim)
            repeat(trim) { held.removeAt(0) }
        }
        val currentRef = queue.current?.ref
        if (held.getOrNull(player.index) != currentRef) return fill()
        val window = queue.window(config.windowBehind, config.windowAhead).map { it.ref }
        val position = currentPosition(window.size)
        val have = player.count - player.index - 1
        val want = window.size - position - 1
        if (want > have) append(window.takeLast(want - have))
    }

    /**
     * Something other than the current song was added to or taken out of the queue, before it or after it. The
     * songs before the current one are swapped in place and the ones after it are replaced, so the current song
     * keeps playing without a gap.
     */
    fun onQueueEdited() {
        val window = queue.window(config.windowBehind, config.windowAhead).map { it.ref }
        val position = currentPosition(window.size)
        if (window.isEmpty() || held.getOrNull(player.index) != queue.current?.ref) return fill()
        if (held.take(player.index) != window.take(position)) {
            if (player.index > 0) {
                held.subList(0, player.index).clear()
                player.removeFirst(player.index)
            }
            val behind = window.take(position)
            if (behind.isNotEmpty()) {
                held.addAll(0, behind)
                player.prepend(behind)
            }
        }
        replaceTail()
    }

    /** Shuffle was toggled: the songs after the current one change, the current one keeps playing. */
    fun onQueueReordered() = replaceTail()

    /**
     * The player could not open [ref]: it leaves the queue and the window is refilled. False when there was
     * nothing to move on to (the failed song was the last one), so the caller can stop.
     */
    fun onItemFailed(ref: SongRef): Boolean {
        val entries = queue.snapshot().entries
        val failed = entries.withIndex().firstOrNull { it.index >= queue.index && it.value.ref == ref }?.index
        return when {
            failed == null -> true
            failed != queue.index -> {
                queue.removeAt(failed)
                replaceTail()
                true
            }
            queue.next() == null -> false
            else -> {
                queue.removeAt(failed)
                fill()
                true
            }
        }
    }

    private fun replaceTail() {
        val window = queue.window(config.windowBehind, config.windowAhead).map { it.ref }
        val position = currentPosition(window.size)
        val behindKept =
            position == player.index && held.take(position) == window.take(position)
        if (window.isEmpty() || !behindKept) return fill()
        if (player.count > position + 1) removeRange(position + 1, player.count)
        val tail = window.drop(position + 1)
        if (tail.isNotEmpty()) append(tail)
    }

    private fun currentPosition(windowSize: Int): Int =
        if (windowSize == 0) 0 else minOf(queue.index, config.windowBehind)

    private fun append(refs: List<SongRef>) {
        held += refs
        player.append(refs)
    }

    private fun removeRange(
        from: Int,
        to: Int,
    ) {
        held.subList(from, to).clear()
        player.removeRange(from, to)
    }
}
