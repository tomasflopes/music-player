package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.DiscoverConfig
import dev.tomasflopes.musicplayer.core.taste.score.SongScore

/**
 * The anchors of sound-aware Discover (ADR 0071): liked songs spread over different sounds, as points in the space.
 *
 * The pool is the best [DiscoverConfig.anchorPool] songs by decayed score that are still in the library, have a
 * positive score and a point in the space. From it, [DiscoverConfig.anchorCount] are taken greedily: the top scorer
 * first, then each time the pool song farthest from every anchor so far (ties go to the higher score). So a corner of
 * the library the owner likes but plays less still gets an anchor, which top scorers alone would not give. The anchors
 * are not averaged: a mean of different sounds lands between them (ADR 0076).
 */
internal object DiscoverAnchors {
    fun choose(
        songScores: List<SongScore>,
        library: List<Song>,
        space: SoundSpace,
        config: DiscoverConfig,
    ): List<DoubleArray> {
        val inLibrary = library.mapTo(HashSet()) { it.id }
        val rows =
            songScores
                .filter { it.score > 0.0 && it.songId in inLibrary }
                .sortedWith(compareByDescending<SongScore> { it.score }.thenBy { it.songId.value })
                .map { space.indexOf(it.songId.value) }
                .filter { it >= 0 }
                .take(config.anchorPool)
        if (rows.isEmpty()) return emptyList()
        return farthestFirst(rows, space, config.anchorCount).map { row ->
            DoubleArray(space.dim) { d -> space.values[row * space.dim + d].toDouble() }
        }
    }

    /** [rows] is best score first; the first is taken, then the farthest from those taken, up to [count]. */
    private fun farthestFirst(
        rows: List<Int>,
        space: SoundSpace,
        count: Int,
    ): List<Int> {
        val chosen = mutableListOf(rows.first())
        val nearestChosen = DoubleArray(rows.size) { space.distance(rows.first(), rows[it]) }
        while (chosen.size < minOf(count, rows.size)) {
            var best = -1
            for (index in rows.indices) {
                if (nearestChosen[index] > (if (best < 0) 0.0 else nearestChosen[best])) best = index
            }
            if (best < 0) break // everything left sounds exactly like an anchor already
            chosen += rows[best]
            for (index in rows.indices) {
                nearestChosen[index] = minOf(nearestChosen[index], space.distance(rows[best], rows[index]))
            }
        }
        return chosen
    }
}
