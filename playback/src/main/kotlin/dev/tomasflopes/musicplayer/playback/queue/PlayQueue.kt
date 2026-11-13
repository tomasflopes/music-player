package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.taste.shuffle.ShuffleStrategy
import kotlin.random.Random

/**
 * The whole play order, in memory (plan C3: the player only holds a window of it). Pure Kotlin.
 *
 * Shuffle is a toggle (ADR 0010): turning it on reorders only the non-manual songs still to come; turning it
 * off puts them back in the order the list had, with the current song staying current. Manual entries
 * (Play next / Add to queue) sit right after the current song, in the order they were added, and never move.
 */
class PlayQueue(
    strategy: ShuffleStrategy,
    random: Random,
) {
    private val ordering = QueueOrdering(strategy, random)
    private val entries = mutableListOf<QueueEntry>()
    private var originalOrder: List<SongRef> = emptyList()
    private var currentIndex = -1

    val current: QueueEntry? get() = entries.getOrNull(currentIndex)
    val index: Int get() = currentIndex
    val size: Int get() = entries.size

    var shuffled: Boolean = false
        private set

    var repeatMode: RepeatMode = RepeatMode.OFF

    /** Plays [songs] now, starting at [startIndex]; manual entries not yet played stay right after it (plan C7). */
    fun replace(
        songs: List<SongRef>,
        startIndex: Int,
        source: PlaySource,
        shuffled: Boolean,
    ) {
        val pendingManual = upcomingManual()
        this.shuffled = shuffled
        originalOrder = songs
        val label = source.withShuffle(shuffled)
        entries.clear()
        entries += ordering.initial(songs, startIndex, shuffled).map { QueueEntry(it, label, manual = false) }
        currentIndex =
            when {
                entries.isEmpty() -> -1
                shuffled -> 0
                else -> startIndex.coerceIn(0, entries.lastIndex)
            }
        entries.addAll(currentIndex + 1, pendingManual)
    }

    /** Shuffle toggle (A1). The current song keeps playing; only what is still to come changes. */
    fun setShuffled(on: Boolean) {
        if (on == shuffled) return
        shuffled = on
        val currentEntry = current
        val played = entries.take(maxOf(currentIndex, 0))
        val manual = upcoming().filter { it.manual }
        val rebuilt = ArrayList<QueueEntry>(entries.size)
        if (on || currentEntry == null || currentEntry.manual) {
            rebuilt += played
            currentEntry?.let { rebuilt += it }
            rebuilt += manual
            val automatic = upcoming().filter { !it.manual }
            rebuilt += if (on) ordering.reorder(automatic) else ordering.restoreOriginal(automatic, originalOrder)
            currentIndex = if (currentEntry == null) -1 else played.size
        } else {
            // Back to the original order with the current song in its own place; manual entries follow it.
            val restored = ordering.restoreOriginal(entries, originalOrder)
            val position = restored.indexOf(currentEntry)
            rebuilt += restored.take(position + 1)
            rebuilt += manual
            rebuilt += restored.drop(position + 1)
            currentIndex = position
        }
        entries.clear()
        entries += ordering.relabel(rebuilt, on)
    }

    fun playNext(songs: List<SongRef>) {
        entries.addAll(currentIndex + 1, songs.map(::manualEntry))
    }

    fun addToQueue(songs: List<SongRef>) {
        entries.addAll(currentIndex + 1 + upcomingManual().size, songs.map(::manualEntry))
    }

    /** The user pressed next. Under repeat ONE this still moves on, and wraps at the end (ADR 0010). */
    fun next(): QueueEntry? = step(forward = true, wraps = repeatMode != RepeatMode.OFF)

    /** The current song finished by itself. */
    fun advanceOnTrackEnd(): QueueEntry? =
        if (repeatMode == RepeatMode.ONE) current else step(forward = true, wraps = repeatMode == RepeatMode.ALL)

    fun previous(): QueueEntry? = step(forward = false, wraps = repeatMode != RepeatMode.OFF)

    fun jumpTo(index: Int): QueueEntry? {
        if (index !in entries.indices) return null
        currentIndex = index
        return current
    }

    /** [behind] entries before the current one, the current one, and [ahead] after it (wrapping under repeat ALL). */
    fun window(
        behind: Int,
        ahead: Int,
    ): List<QueueEntry> {
        if (entries.isEmpty()) return emptyList()
        val from = (currentIndex - behind).coerceAtLeast(0)
        val to = (currentIndex + ahead).coerceAtMost(entries.lastIndex)
        val missing = currentIndex + ahead - entries.lastIndex
        val wrapped =
            if (repeatMode == RepeatMode.ALL &&
                missing > 0
            ) {
                entries.take(minOf(missing, from))
            } else {
                emptyList()
            }
        return entries.subList(from, to + 1).toList() + wrapped
    }

    fun removeAt(index: Int) {
        if (index !in entries.indices || index == currentIndex) return
        entries.removeAt(index)
        if (index < currentIndex) currentIndex--
    }

    fun snapshot() = QueueSnapshot(entries.toList(), originalOrder, currentIndex, shuffled, repeatMode)

    fun restore(snapshot: QueueSnapshot) {
        entries.clear()
        entries += snapshot.entries
        originalOrder = snapshot.originalOrder
        currentIndex = snapshot.index.coerceIn(-1, entries.lastIndex)
        shuffled = snapshot.shuffled
        repeatMode = snapshot.repeatMode
    }

    private fun manualEntry(ref: SongRef) = QueueEntry(ref, PlaySource.QUEUE, manual = true)

    private fun step(
        forward: Boolean,
        wraps: Boolean,
    ): QueueEntry? {
        if (entries.isEmpty()) return null
        val target = currentIndex + if (forward) 1 else -1
        return when {
            target in entries.indices -> jumpTo(target)
            !wraps -> null
            forward -> wrapToStart()
            else -> jumpTo(entries.lastIndex)
        }
    }

    /** Repeat reached the end: start over, reshuffled when shuffle is on. */
    private fun wrapToStart(): QueueEntry? {
        if (shuffled) {
            val reordered = ordering.reorder(entries.filter { !it.manual })
            entries.clear()
            entries += reordered
        }
        return jumpTo(0)
    }

    /** A view, not a copy: the entries after the current one. */
    private fun upcoming(): List<QueueEntry> = entries.subList(currentIndex + 1, entries.size)

    /** The manual entries waiting right after the current song. */
    private fun upcomingManual(): List<QueueEntry> = upcoming().takeWhile { it.manual }
}
