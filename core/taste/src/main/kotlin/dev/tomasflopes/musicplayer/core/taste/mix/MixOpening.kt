package dev.tomasflopes.musicplayer.core.taste.mix

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeedKind
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.lists.Candidate
import dev.tomasflopes.musicplayer.core.taste.lists.duplicateKey

/**
 * The songs that open a mix batch (ADR 0076): for every artist seed, its own songs closest to its sound, so a mix
 * always plays the artists it was made from. The distance-only ranking does not guarantee that: a seed artist's songs
 * can sit far from their own centroid while other artists' songs sit close to it.
 */
internal object MixOpening {
    fun of(
        seeds: List<MixSeed>,
        candidates: List<Candidate>,
        space: SoundSpace,
        perArtist: Int,
    ): List<Song> {
        if (perArtist <= 0) return emptyList()
        return seeds
            .filter { it.kind == MixSeedKind.ARTIST }
            .flatMap { seed -> closestOf(seed.refId, candidates, space, perArtist) }
            .distinctBy { it.id }
            .distinctBy { it.duplicateKey() }
    }

    private fun closestOf(
        artistId: Long,
        candidates: List<Candidate>,
        space: SoundSpace,
        count: Int,
    ): List<Song> {
        val own = candidates.filter { it.song.artistId.value == artistId }
        if (own.isEmpty()) return emptyList()
        val centre = space.centroid(own.map { it.row })
        return own
            .sortedWith(compareBy<Candidate> { space.distanceTo(it.row, centre) }.thenBy { it.song.id.value })
            .distinctBy { it.song.duplicateKey() }
            .take(count)
            .map { it.song }
    }
}
