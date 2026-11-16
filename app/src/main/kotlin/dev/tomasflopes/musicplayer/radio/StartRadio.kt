package dev.tomasflopes.musicplayer.radio

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import dev.tomasflopes.musicplayer.core.taste.lists.RadioPolicy
import dev.tomasflopes.musicplayer.playback.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * Starts a radio from one song (design §7.6, ADR 0072): the batch replaces the queue and plays from the seed. Both
 * entry points, the song menu and Now playing, call this and nothing else; the choice of songs is [RadioPolicy]'s.
 * A seed with no sound profile plays nothing and says so.
 */
class StartRadio(
    private val songs: SongReader,
    private val vectors: SoundVectorSource,
    private val played: PlayedSinceSource,
    private val playback: PlaybackController,
    private val messages: ShortMessages,
    private val policy: RadioPolicy,
    private val config: RadioConfig,
    private val clock: Clock,
) {
    suspend operator fun invoke(seedId: SongId) {
        val seed = songs.songsByIds(listOf(seedId)).firstOrNull() ?: return
        val batch = batchFor(seed)
        if (batch.isEmpty()) messages.show(NO_PROFILE) else playback.play(radioRequest(batch))
    }

    /** The batch for [seed], or an empty list when the seed has no point in the sound space. */
    private suspend fun batchFor(seed: Song): List<Song> {
        val space = vectors.space()
        if (space.indexOf(seed.id.value) < 0) return emptyList()
        val library = songs.observeSongs().first()
        val recent = recentlyPlayed(played, clock, config)
        val random = radioRandom(config)
        return withContext(Dispatchers.Default) { policy.pick(seed, library, space, recent, random) }
    }

    companion object {
        const val NO_PROFILE = "No sound profile for this song yet"
    }
}
