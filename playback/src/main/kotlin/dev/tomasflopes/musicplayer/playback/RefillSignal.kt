package dev.tomasflopes.musicplayer.playback

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed

/**
 * What the refiller needs to decide whether to extend the queue and to build the next batch (ADR 0078). The engine
 * publishes it only for a seeded queue whose repeat mode is OFF.
 *
 * @property seed the seed of the last non-manual entry in the queue.
 * @property nonManualAhead non-manual entries after the current one.
 * @property queued every entry of the queue, played ones included, so a batch never repeats a song.
 * @property lastArtists main artists of the last entries of the queue, oldest first, for the artist gap.
 * @property epoch counts queue replacements and restores; a batch built for another epoch is dropped.
 */
data class RefillSignal(
    val seed: RefillSeed,
    val nonManualAhead: Int,
    val queued: List<SongRef>,
    val lastArtists: List<ArtistId>,
    val epoch: Int,
)
