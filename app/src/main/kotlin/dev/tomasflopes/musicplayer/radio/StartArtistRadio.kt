package dev.tomasflopes.musicplayer.radio

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import dev.tomasflopes.musicplayer.core.taste.lists.ArtistRadioPolicy
import dev.tomasflopes.musicplayer.playback.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * Starts a radio from an artist's sound (ADR 0075): the batch replaces the queue, like the song radio. The choice of
 * songs is [ArtistRadioPolicy]'s. An artist with no profiled song plays nothing and says so.
 */
class StartArtistRadio(
    private val songs: SongReader,
    private val vectors: SoundVectorSource,
    private val played: PlayedSinceSource,
    private val playback: PlaybackController,
    private val messages: ShortMessages,
    private val policy: ArtistRadioPolicy,
    private val config: RadioConfig,
    private val clock: Clock,
) {
    suspend operator fun invoke(artistId: ArtistId) {
        val space = vectors.space()
        val library = songs.observeSongs().first()
        val recent = recentlyPlayed(played, clock, config)
        val random = radioRandom(config)
        val batch = withContext(Dispatchers.Default) { policy.pick(artistId, library, space, recent, random) }
        if (batch.isEmpty()) messages.show(NO_PROFILE) else playback.play(radioRequest(batch))
    }

    companion object {
        const val NO_PROFILE = "No sound profiles for this artist yet"
    }
}
