package dev.tomasflopes.musicplayer.core.taste.shuffle

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.taste.config.SmartShuffleConfig
import java.util.TreeSet
import kotlin.random.Random

/**
 * Random order with no song by the same main artist inside the next [SmartShuffleConfig.artistGap] tracks
 * (design §7.8, ADR 0035). When the list cannot be spaced that far (few artists, or one with too many songs) the gap
 * is relaxed to the widest one that fits; whatever still cannot be spaced is placed as far apart as it can.
 */
class SmartShuffle(
    private val config: SmartShuffleConfig = SmartShuffleConfig(),
) : ShuffleStrategy {
    override fun order(
        items: List<SongRef>,
        random: Random,
    ): List<SongRef> = orderAfter(emptyList(), items, random)

    override fun orderAfter(
        preceding: List<SongRef>,
        items: List<SongRef>,
        random: Random,
    ): List<SongRef> {
        if (items.size < 2) return items.toList()
        return spaced(preceding, items, random)
    }

    private fun spaced(
        preceding: List<SongRef>,
        items: List<SongRef>,
        random: Random,
    ): List<SongRef> {
        val byArtist = LinkedHashMap<ArtistId, MutableList<SongRef>>()
        for (item in items) byArtist.getOrPut(item.mainArtistId) { ArrayList() }.add(item)
        val gap = relaxedGap(byArtist.values.maxOf { it.size }, items.size)
        if (gap == 0) return items.shuffled(random)
        byArtist.values.forEach { it.shuffle(random) }
        return ArtistSpacer(byArtist, gap, preceding.takeLast(gap), random).place(items.size)
    }

    /** The widest gap, up to the configured one, at which the biggest artist's songs still fit in [total]. */
    private fun relaxedGap(
        largest: Int,
        total: Int,
    ): Int = if (largest <= 1) config.artistGap else minOf(config.artistGap, (total - largest) / (largest - 1))
}

/**
 * Places songs one by one: a random song among the artists not placed in the last [gap] tracks, except that an
 * artist who would no longer fit if skipped goes first. Artists are drawn in proportion to their songs left, so
 * the draw is uniform over the eligible songs. O(n log artists).
 */
private class ArtistSpacer(
    byArtist: Map<ArtistId, MutableList<SongRef>>,
    private val gap: Int,
    preceding: List<SongRef>,
    private val random: Random,
) {
    private val songs = byArtist.values.toList()
    private val remaining = IntArray(songs.size) { songs[it].size }
    private val eligible = BooleanArray(songs.size)
    private val cooling = BooleanArray(songs.size)
    private val lastPosition = IntArray(songs.size)
    private val weights = Fenwick(songs.size)
    private var weightTotal = 0
    private val byRemaining = TreeSet<Long>()
    private val cooldown = ArrayDeque<Cooldown>()

    private class Cooldown(
        val artist: Int,
        val position: Int,
    )

    init {
        val index = byArtist.keys.withIndex().associate { it.value to it.index }
        preceding.forEachIndexed { i, ref ->
            val artist = index[ref.mainArtistId] ?: return@forEachIndexed
            startCooldown(artist, i - preceding.size)
        }
        songs.indices.filterNot { cooling[it] }.forEach(::enable)
    }

    fun place(total: Int): List<SongRef> {
        val out = ArrayList<SongRef>(total)
        for (position in 0 until total) {
            release(position)
            val artist = urgent(total - position) ?: sample() ?: oldestCooling() ?: anyLeft()
            disable(artist)
            out += songs[artist].removeAt(songs[artist].lastIndex)
            remaining[artist]--
            startCooldown(artist, position)
        }
        return out
    }

    private fun startCooldown(
        artist: Int,
        position: Int,
    ) {
        cooling[artist] = true
        lastPosition[artist] = position
        cooldown.addLast(Cooldown(artist, position))
    }

    /** Artists placed at least [gap] + 1 tracks ago may play again. */
    private fun release(position: Int) {
        while (cooldown.isNotEmpty() && cooldown.first().position + gap + 1 <= position) {
            val entry = cooldown.removeFirst()
            if (isCurrent(entry) && cooling[entry.artist]) {
                cooling[entry.artist] = false
                if (remaining[entry.artist] > 0) enable(entry.artist)
            }
        }
    }

    private fun isCurrent(entry: Cooldown) = lastPosition[entry.artist] == entry.position

    /** The eligible artist with the most songs left, once skipping it would leave it (almost) too little room. */
    private fun urgent(left: Int): Int? {
        if (byRemaining.isEmpty()) return null
        val top = byRemaining.last()
        val count = (top shr COUNT_SHIFT).toInt()
        return if ((count - 1) * (gap + 1) + 1 + gap >= left) (top and ARTIST_MASK).toInt() else null
    }

    private fun sample(): Int? = if (weightTotal == 0) null else weights.find(random.nextInt(weightTotal))

    /** Nobody is free: relax by letting the artist who has waited longest play. */
    private fun oldestCooling(): Int? {
        while (cooldown.isNotEmpty()) {
            val entry = cooldown.removeFirst()
            if (isCurrent(entry) && cooling[entry.artist] && remaining[entry.artist] > 0) {
                cooling[entry.artist] = false
                return entry.artist
            }
        }
        return null
    }

    private fun anyLeft(): Int = remaining.indexOfFirst { it > 0 }

    private fun enable(artist: Int) {
        if (eligible[artist]) return
        eligible[artist] = true
        weights.add(artist, remaining[artist])
        weightTotal += remaining[artist]
        byRemaining.add(key(artist))
    }

    private fun disable(artist: Int) {
        if (!eligible[artist]) return
        eligible[artist] = false
        weights.add(artist, -remaining[artist])
        weightTotal -= remaining[artist]
        byRemaining.remove(key(artist))
    }

    private fun key(artist: Int): Long = (remaining[artist].toLong() shl COUNT_SHIFT) or artist.toLong()

    private companion object {
        const val COUNT_SHIFT = 32
        const val ARTIST_MASK = 0xFFFFFFFFL
    }
}

/** Prefix sums over artist weights, to draw an artist in proportion to its weight. */
private class Fenwick(
    private val size: Int,
) {
    private val tree = IntArray(size + 1)

    fun add(
        index: Int,
        delta: Int,
    ) {
        var i = index + 1
        while (i <= size) {
            tree[i] += delta
            i += i and -i
        }
    }

    /** The index whose weight range holds [target], 0 <= target < total. */
    fun find(target: Int): Int {
        var position = 0
        var rest = target
        var step = Integer.highestOneBit(size)
        while (step > 0) {
            val next = position + step
            if (next <= size && tree[next] <= rest) {
                position = next
                rest -= tree[next]
            }
            step = step shr 1
        }
        return position
    }
}
