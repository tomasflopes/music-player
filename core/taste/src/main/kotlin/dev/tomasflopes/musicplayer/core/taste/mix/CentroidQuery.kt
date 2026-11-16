package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig

/**
 * The alternative rule, kept so both can be compared (ADR 0076): one query point, the mean of the anchors, and a key
 * of the distance to it divided by its reach (same units as [AnchorQuery]). Seeds from different sounds average to a
 * point between them where there may be no song at all.
 */
class CentroidQuery(
    private val config: MixConfig = MixConfig(),
) : MixQuery {
    override fun ranking(
        space: SoundSpace,
        anchors: List<DoubleArray>,
    ): MixRanking? {
        if (anchors.isEmpty()) return null
        val centre = DoubleArray(space.dim) { d -> anchors.sumOf { it[d] } / anchors.size }
        val reach = reachOf(space, centre, config.densityRank)
        return MixRanking { row -> space.distanceTo(row, centre) / reach }
    }
}
