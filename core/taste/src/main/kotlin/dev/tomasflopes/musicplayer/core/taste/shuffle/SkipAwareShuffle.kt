package dev.tomasflopes.musicplayer.core.taste.shuffle

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.taste.config.SkipAwareShuffleConfig
import dev.tomasflopes.musicplayer.core.taste.config.SmartShuffleConfig
import kotlin.math.ln
import kotlin.random.Random

/**
 * Shuffle that plays songs the owner keeps skipping later (design §7.7, ADR 0050). Songs with no score or a score
 * of 0 or more are ordered by [base] (smart shuffle, so the artist spacing stays); each song with a negative global
 * score is then placed at a random point whose expected place moves towards the end as the score drops, never
 * removed. With no negative songs the order is exactly [base]'s.
 */
class SkipAwareShuffle(
    private val scores: GlobalScores,
    private val base: ShuffleStrategy = SmartShuffle(),
    private val config: SkipAwareShuffleConfig = SkipAwareShuffleConfig(),
    private val artistGap: Int = SmartShuffleConfig().artistGap,
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
        val snapshot = scores.snapshot()
        val (skipped, neutral) = items.partition { (snapshot[it.songId] ?: 0.0) < 0.0 }
        if (skipped.isEmpty()) return base.orderAfter(preceding, items, random)
        val ordered = base.orderAfter(preceding, neutral, random)
        val due = skipped.map { Due(it, dueIndex(snapshot.getValue(it.songId), ordered.size, random)) }
        return merge(preceding, ordered, due.sortedBy { it.index })
    }

    private class Due(
        val song: SongRef,
        val index: Int,
    )

    /**
     * Efraimidis-Spirakis: a weighted random order sorts by u^(1/w). Here the key picks the point of the neutral
     * list after which the song becomes due: uniform for weight 1, later for a smaller weight.
     */
    private fun dueIndex(
        score: Double,
        neutralSize: Int,
        random: Random,
    ): Int {
        val weight = config.weightOf(score)
        val key = Math.exp(ln(1.0 - random.nextDouble()) / weight)
        return ((1.0 - key) * (neutralSize + 1)).toInt().coerceIn(0, neutralSize)
    }

    /** The first waiting song whose artist is not among the last and the next [artistGap] tracks. */
    private fun firstThatFits(
        waiting: List<SongRef>,
        preceding: List<SongRef>,
        out: List<SongRef>,
        neutral: List<SongRef>,
        neutralIndex: Int,
    ): SongRef? {
        val near = HashSet<ArtistId>()
        for (back in 1..artistGap) {
            val ref = out.getOrNull(out.size - back) ?: preceding.getOrNull(preceding.size - (back - out.size))
            if (ref != null) near += ref.mainArtistId
        }
        for (i in neutralIndex until minOf(neutral.size, neutralIndex + artistGap)) near += neutral[i].mainArtistId
        return waiting.firstOrNull { it.mainArtistId !in near }
    }

    /** Walks [neutral]; a due song goes in at the first place that keeps it [artistGap] tracks from its artist. */
    private fun merge(
        preceding: List<SongRef>,
        neutral: List<SongRef>,
        sorted: List<Due>,
    ): List<SongRef> {
        val out = ArrayList<SongRef>(neutral.size + sorted.size)
        val waiting = ArrayDeque<SongRef>()
        var next = 0
        var neutralIndex = 0
        while (neutralIndex < neutral.size || next < sorted.size || waiting.isNotEmpty()) {
            while (next < sorted.size && sorted[next].index <= neutralIndex) waiting.addLast(sorted[next++].song)
            val fits = if (waiting.isEmpty()) null else firstThatFits(waiting, preceding, out, neutral, neutralIndex)
            when {
                fits != null -> {
                    waiting.remove(fits)
                    out += fits
                }
                neutralIndex < neutral.size -> out += neutral[neutralIndex++]
                else -> out += waiting.removeFirst()
            }
        }
        return out
    }
}
