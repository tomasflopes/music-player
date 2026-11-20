package dev.tomasflopes.musicplayer.refill

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import dev.tomasflopes.musicplayer.core.taste.config.SoloExtensionConfig
import dev.tomasflopes.musicplayer.core.taste.lists.RadioPolicy
import dev.tomasflopes.musicplayer.playback.RefillSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Extends a single song chosen from Search (ADR 0080): the next [SoloExtensionConfig.batchSize] songs that sound like
 * it, from [RadioPolicy.refill], logged as `radio` and never picked. The anchor stays the chosen song for the whole
 * queue. A song with no sound profile gives nothing, silently. Off when "Extend single songs" is off.
 */
internal class SoloRefillSource(
    private val settings: SettingsRepository,
    private val vectors: SoundVectorSource,
    private val library: RefillLibrary,
    private val policy: RadioPolicy,
    private val config: SoloExtensionConfig,
    private val radio: RadioConfig,
) : RefillSource {
    override fun handles(seed: RefillSeed) = seed is RefillSeed.Song

    override val lowWater: Int get() = config.lowWater

    override suspend fun enabled(): Boolean = settings.extendSingleSongs.first()

    override suspend fun batch(signal: RefillSignal): RefillBatch {
        val seedId = (signal.seed as? RefillSeed.Song)?.id ?: return RefillBatch.NONE
        return withContext(Dispatchers.Default) {
            val space = vectors.space()
            if (!space.has(seedId)) return@withContext RefillBatch.NONE
            val songs = library.library()
            val seed = songs.firstOrNull { it.id.value == seedId } ?: return@withContext RefillBatch.NONE
            val batch =
                policy.refill(
                    seed = seed,
                    library = songs,
                    space = space,
                    recentlyPlayed = library.recentlyPlayed(radio.recentDays),
                    random = radio.seed?.let { Random(it) } ?: Random.Default,
                    exclude = library.queued(signal, songs),
                    precedingArtists = signal.lastArtists,
                )
            RefillBatch(batch, PlaySource.RADIO)
        }
    }
}
