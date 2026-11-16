package dev.tomasflopes.musicplayer.mix

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.model.mixes.MixReader
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.mix.MixEngine
import dev.tomasflopes.musicplayer.playback.PlayRequest
import dev.tomasflopes.musicplayer.playback.PlaybackController
import dev.tomasflopes.musicplayer.radio.ShortMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import kotlin.random.Random

private const val MS_PER_DAY = 86_400_000L

/**
 * Starts a mix (design §7.6, ADR 0076): one batch of 25 replaces the queue and plays from its first song, logged as
 * `mix` with the mix id. The choice of songs is [MixEngine]'s. No refill and no learning in this slice. A mix whose
 * seeds have no sound profile plays nothing and says so; a mix that no longer exists does nothing.
 */
class StartMix(
    private val mixes: MixReader,
    private val songs: SongReader,
    private val vectors: SoundVectorSource,
    private val played: PlayedSinceSource,
    private val playback: PlaybackController,
    private val messages: ShortMessages,
    private val engine: MixEngine,
    private val config: MixConfig,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: MixId) {
        val mix = mixes.mix(id) ?: return
        val space = vectors.space()
        val library = songs.observeSongs().first()
        val recent = recentlyPlayed()
        val random = config.seed?.let { Random(it) } ?: Random.Default
        val batch = withContext(Dispatchers.Default) { engine.pick(mix.seeds, library, space, recent, random) }
        if (batch.isEmpty()) messages.show(NO_PROFILE) else playback.play(mixRequest(batch, id))
    }

    private suspend fun recentlyPlayed(): Set<SongId> =
        played.observePlayedIdsSince(clock.millis() - config.recentDays * MS_PER_DAY).first()

    companion object {
        const val NO_PROFILE = "No sound profile for this mix yet"
    }
}

/** The batch as one request: replaces the queue, starts at the first song, logged as `mix` and picked. */
internal fun mixRequest(
    batch: List<Song>,
    id: MixId,
) = PlayRequest(
    songs = batch.map { it.ref },
    startIndex = 0,
    shuffle = false,
    source = PlaySource.MIX,
    picked = true,
    mixId = id.value,
)
