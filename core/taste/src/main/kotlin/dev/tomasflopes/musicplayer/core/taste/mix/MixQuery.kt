package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace

/** The rank key of every song of a [SoundSpace] for one mix; lower is closer to the mix. */
fun interface MixRanking {
    fun keyOf(row: Int): Double
}

/**
 * How a mix turns its anchor points into a rank key (ADR 0076). An anchor is a point in the space: a song seed's own
 * point or the centroid of an artist seed (ADR 0075). Behind an interface so [AnchorQuery] (the default) and
 * [CentroidQuery] can be compared on the same seeds (CLAUDE.md, decision points).
 */
interface MixQuery {
    /** The ranking for [anchors], or `null` when there are none. */
    fun ranking(
        space: SoundSpace,
        anchors: List<DoubleArray>,
    ): MixRanking?
}
