package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig

/** Smallest reach an anchor may have, so a space of identical points never divides by zero. */
private const val MIN_REACH = 1e-9

/**
 * The distance from [point] to its [rank]th nearest song (the last one there is, in a small space). This is how far
 * that sound spreads around the point; the rank keys of [AnchorQuery] and [CentroidQuery] are measured in it.
 */
internal fun reachOf(
    space: SoundSpace,
    point: DoubleArray,
    rank: Int,
): Double = maxOf(space.nearestTo(point, rank).lastOrNull()?.distance ?: 0.0, MIN_REACH)

/**
 * The default rule (ADR 0076): a song's key is its distance to the nearest anchor divided by that anchor's reach, so
 * every seed pulls its own neighbourhood and a seed in a crowded corner does not drown one in a sparse corner. Seeds
 * from different sounds stay different sounds; nothing is averaged.
 */
class AnchorQuery(
    private val config: MixConfig = MixConfig(),
) : MixQuery {
    override fun ranking(
        space: SoundSpace,
        anchors: List<DoubleArray>,
    ): MixRanking? {
        if (anchors.isEmpty()) return null
        val reaches = anchors.map { reachOf(space, it, config.densityRank) }
        return MixRanking { row ->
            anchors.indices.minOf { space.distanceTo(row, anchors[it]) / reaches[it] }
        }
    }
}
