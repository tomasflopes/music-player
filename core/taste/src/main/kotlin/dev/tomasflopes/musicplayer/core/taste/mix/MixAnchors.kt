package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeedKind
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace

/**
 * Turns seeds into anchor points (ADR 0076): a song seed is its own point, an artist seed is the centroid of the
 * profiled songs whose main artist it is (ADR 0075). A seed with no point in the space gives no anchor.
 */
internal object MixAnchors {
    fun of(
        seeds: List<MixSeed>,
        library: List<Song>,
        space: SoundSpace,
    ): List<DoubleArray> {
        val rowsByArtist = HashMap<Long, MutableList<Int>>()
        library.forEach { song ->
            val row = space.indexOf(song.id.value)
            if (row >= 0) rowsByArtist.getOrPut(song.artistId.value) { mutableListOf() } += row
        }
        return seeds.mapNotNull { seed ->
            when (seed.kind) {
                MixSeedKind.SONG ->
                    space
                        .indexOf(seed.refId)
                        .takeIf { it >= 0 }
                        ?.let { row -> DoubleArray(space.dim) { d -> space.values[row * space.dim + d].toDouble() } }
                MixSeedKind.ARTIST -> rowsByArtist[seed.refId]?.let(space::centroid)
            }
        }
    }
}
