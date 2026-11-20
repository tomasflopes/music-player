package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.core.taste.shuffle.ShuffleStrategy
import dev.tomasflopes.musicplayer.playback.QueueContent
import dev.tomasflopes.musicplayer.playback.RefillSignal
import kotlin.random.Random

/**
 * The whole play order, in memory (plan C3: the player only holds a window of it). Pure Kotlin.
 *
 * Shuffle is a toggle (ADR 0010): turning it on reorders only the non-manual songs still to come; turning it
 * off puts them back in the order the list had, with the current song staying current. Manual entries
 * (Play next / Add to queue) sit right after the current song, in the order they were added, and never move.
 */
@Suppress("TooManyFunctions")
class PlayQueue(
    strategy: ShuffleStrategy,
    random: Random,
    /** Orders artist shuffles, which are exempt from smart shuffle's artist spacing (design 7.8, ADR 0035). */
    artistShuffle: ShuffleStrategy = strategy,
    /** Orders shuffles of the Recently added list, which play songs the owner keeps skipping later (ADR 0050). */
    recentlyAdded: ShuffleStrategy = strategy,
) {
    private val ordering = QueueOrdering(strategy, random, artistShuffle, recentlyAdded)
    private val entries = mutableListOf<QueueEntry>()
    private var originalOrder: List<SongRef> = emptyList()
    private var currentIndex = -1

    val current: QueueEntry? get() = entries.getOrNull(currentIndex)
    val index: Int get() = currentIndex
    val size: Int get() = entries.size

    var shuffled: Boolean = false
        private set

    var repeatMode: RepeatMode = RepeatMode.OFF

    /**
     * Counts [replace] and [restore] calls (ADR 0078). A batch built for an earlier epoch belongs to a queue that is
     * gone and is dropped; [append] does not move it.
     */
    var epoch: Int = 0
        private set

    /**
     * Plays [songs] now, starting at [startIndex]; manual entries not yet played stay right after it (plan C7).
     * With [shuffled] and [randomStart] the first song is the shuffle's too, not [startIndex]'s.
     */
    fun replace(
        songs: List<SongRef>,
        startIndex: Int,
        source: PlaySource,
        shuffled: Boolean,
        randomStart: Boolean = false,
        playlistId: Long? = null,
        mixId: Long? = null,
        soloSeed: Long? = null,
    ) {
        epoch++
        val pendingManual = upcomingManual()
        this.shuffled = shuffled
        originalOrder = songs
        val label = source.withShuffle(shuffled)
        entries.clear()
        val manualAhead = pendingManual.map { it.ref }
        val order = ordering.initial(songs, startIndex, source, shuffled, randomStart, manualAhead)
        entries += order.map { QueueEntry(it, label, manual = false, playlistId, mixId, soloSeed) }
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
            rebuilt +=
                if (on) {
                    ordering.reorder(automatic, rebuilt.map { it.ref })
                } else {
                    ordering.restoreOriginal(automatic, originalOrder)
                }
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

    /**
     * Adds a refill batch at the tail (ADR 0078): non-manual entries carrying [seed], logged with [source]. They come
     * after everything queued, manual entries included, and join the original order so turning shuffle off keeps them.
     */
    fun append(
        songs: List<SongRef>,
        source: PlaySource,
        seed: RefillSeed,
    ) {
        val label = source.withShuffle(shuffled)
        val mixId = (seed as? RefillSeed.Mix)?.id
        val soloSeed = (seed as? RefillSeed.Song)?.id
        entries += songs.map { QueueEntry(it, label, manual = false, mixId = mixId, soloSeed = soloSeed) }
        originalOrder = originalOrder + songs
    }

    /**
     * What a refill needs to know, or null when the queue is empty, repeat is not OFF (a looping queue is not
     * extended), or no non-manual entry has a seed. The seed is the last non-manual entry's, so a manual song playing
     * with no auto songs left still extends the queue.
     */
    fun refillSignal(): RefillSignal? {
        if (entries.isEmpty() || repeatMode != RepeatMode.OFF) return null
        val seed = entries.lastOrNull { !it.manual }?.refillSeed ?: return null
        return RefillSignal(
            seed = seed,
            nonManualAhead = upcoming().count { !it.manual },
            queued = entries.map { it.ref },
            lastArtists = entries.takeLast(LAST_ARTISTS).map { it.ref.mainArtistId },
            epoch = epoch,
        )
    }

    /** The user pressed next. Under repeat ONE this still moves on, and wraps at the end (ADR 0010). */
    fun next(): QueueEntry? = step(forward = true, wraps = repeatMode != RepeatMode.OFF)

    /** The current song finished by itself. */
    fun advanceOnTrackEnd(): QueueEntry? =
        if (repeatMode == RepeatMode.ONE) current else step(forward = true, wraps = repeatMode == RepeatMode.ALL)

    /**
     * The entry [advanceOnTrackEnd] would move to, without moving. Null at the end of the queue, and when repeat ALL
     * wraps a shuffled queue, because the wrap reshuffles and the next song is not known yet.
     */
    fun peekAdvance(): QueueEntry? =
        when {
            entries.isEmpty() -> null
            repeatMode == RepeatMode.ONE -> current
            currentIndex + 1 in entries.indices -> entries[currentIndex + 1]
            repeatMode == RepeatMode.ALL && !shuffled -> entries.first()
            else -> null
        }

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

    fun entryAt(index: Int): QueueEntry? = entries.getOrNull(index)

    /**
     * Where an upcoming entry at [from] would land when dragged to [to] (ADR 0081), or null when it cannot move: the
     * current entry, played ones and unknown places stay put, and no move to the same place counts. Manual entries come
     * before auto ones, so a drag stops at the edge of its own group.
     */
    fun moveTarget(
        from: Int,
        to: Int,
    ): Int? {
        if (from <= currentIndex || from !in entries.indices) return null
        val manual = entries[from].manual
        var low = from
        while (low - 1 > currentIndex && entries[low - 1].manual == manual) low--
        var high = from
        while (high + 1 <= entries.lastIndex && entries[high + 1].manual == manual) high++
        return to.coerceIn(low, high).takeIf { it != from }
    }

    /**
     * Drags the upcoming entry at [from] to [to] (clamped by [moveTarget]); false when nothing moved. A reordered auto
     * song also changes its slot in the original order, so turning shuffle off keeps the reorder (ADR 0081, as 0038).
     */
    fun move(
        from: Int,
        to: Int,
    ): Boolean {
        val target = moveTarget(from, to) ?: return false
        val manual = entries[from].manual
        val before = if (manual) emptyList() else upcoming().filter { !it.manual }.map { it.ref }
        entries.add(target, entries.removeAt(from))
        if (!manual) reassignOriginalSlots(before, upcoming().filter { !it.manual }.map { it.ref })
        return true
    }

    /** The slots [before] held in the original order go to [after], in that order; nothing changes if they differ. */
    private fun reassignOriginalSlots(
        before: List<SongRef>,
        after: List<SongRef>,
    ) {
        val moved = before.toHashSet()
        val slots = originalOrder.indices.filter { originalOrder[it] in moved }
        if (slots.size != after.size) return
        val rewritten = originalOrder.toMutableList()
        slots.zip(after).forEach { (slot, ref) -> rewritten[slot] = ref }
        originalOrder = rewritten
    }

    /**
     * Takes the entry at [index] out, current one included (ADR 0047). The current index follows: an earlier entry
     * moves it back by one, and when the current entry itself goes, the entry after it becomes current (or the one
     * before it when it was the last; -1 when nothing is left).
     */
    fun remove(index: Int): QueueEntry? {
        val entry = entries.getOrNull(index) ?: return null
        entries.removeAt(index)
        when {
            index < currentIndex -> currentIndex--
            index == currentIndex -> currentIndex = if (entries.isEmpty()) -1 else minOf(index, entries.lastIndex)
        }
        return entry
    }

    /** Puts [entry] back at [index] (clamped); the current song stays the same song unless it was the empty queue. */
    fun insertAt(
        index: Int,
        entry: QueueEntry,
    ) {
        val at = index.coerceIn(0, entries.size)
        entries.add(at, entry)
        if (currentIndex >= 0 && at <= currentIndex) currentIndex++
    }

    /**
     * Like [window], with each entry's place in the whole queue (-1 for an entry that repeat ALL wrapped round),
     * and no more than [behind] earlier entries.
     */
    fun indexedWindow(
        behind: Int,
        ahead: Int,
    ): List<Pair<Int, QueueEntry>> {
        val start = (currentIndex - behind).coerceAtLeast(0)
        return window(behind, ahead).mapIndexed { i, entry ->
            (if (start + i < entries.size) start + i else -1) to entry
        }
    }

    /** The current song and everything after it; see [QueueContent]. */
    fun content(): QueueContent {
        val tail = entries.drop(maxOf(currentIndex, 0))
        return QueueContent(tail.map { it.ref.songId }, tail.filter { !it.manual }.map { it.source }.toSet())
    }

    fun snapshot() = QueueSnapshot(entries.toList(), originalOrder, currentIndex, shuffled, repeatMode)

    fun restore(snapshot: QueueSnapshot) {
        entries.clear()
        entries += snapshot.entries
        originalOrder = snapshot.originalOrder
        currentIndex = snapshot.index.coerceIn(-1, entries.lastIndex)
        shuffled = snapshot.shuffled
        repeatMode = snapshot.repeatMode
        epoch++
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
            val reordered = ordering.reorder(entries.filter { !it.manual }, entries.map { it.ref })
            entries.clear()
            entries += reordered
        }
        return jumpTo(0)
    }

    /** A view, not a copy: the entries after the current one. */
    private fun upcoming(): List<QueueEntry> = entries.subList(currentIndex + 1, entries.size)

    /** The manual entries waiting right after the current song. */
    private fun upcomingManual(): List<QueueEntry> = upcoming().takeWhile { it.manual }

    private companion object {
        /** The artist gap never looks further back than this (smart shuffle's largest gap). */
        const val LAST_ARTISTS = 4
    }
}
