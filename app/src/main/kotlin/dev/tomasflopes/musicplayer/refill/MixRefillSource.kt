package dev.tomasflopes.musicplayer.refill

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.model.mixes.MixReader
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.core.taste.config.MixConfig
import dev.tomasflopes.musicplayer.core.taste.mix.MixEngine
import dev.tomasflopes.musicplayer.core.taste.mix.MixScoreBoard
import dev.tomasflopes.musicplayer.playback.RefillSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Refills a playing mix (design §7.6, ADR 0078): the next [MixConfig.batchSize] songs from [MixEngine.refill], logged
 * as `mix`. The mix is read again at each refill, so an edit applies at the next one and a deleted mix gives nothing.
 * What the mix learned from its own rows ([MixScoreBoard], ADR 0085) is read again too, so a skip a minute ago shapes
 * the next batch (refill batches log as `mix`, ADR 0079, and so feed the mix score).
 */
class MixRefillSource(
    private val mixes: MixReader,
    private val vectors: SoundVectorSource,
    private val library: RefillLibrary,
    private val scores: MixScoreBoard,
    private val engine: MixEngine,
    private val config: MixConfig,
) : RefillSource {
    override fun handles(seed: RefillSeed) = seed is RefillSeed.Mix

    override val lowWater: Int get() = config.refillLowWater

    override suspend fun enabled() = true

    override suspend fun batch(signal: RefillSignal): RefillBatch {
        val seed = signal.seed as? RefillSeed.Mix ?: return RefillBatch.NONE
        return withContext(Dispatchers.Default) {
            val mix = mixes.mix(MixId(seed.id)) ?: return@withContext RefillBatch.NONE
            val songs = library.library()
            val learning = scores.of(MixId(seed.id), library.now())
            val songsBatch =
                engine.refill(
                    seeds = mix.seeds,
                    library = songs,
                    space = vectors.space(),
                    recentlyPlayed = library.recentlyPlayed(config.recentDays),
                    random = config.seed?.let { Random(it) } ?: Random.Default,
                    exclude = library.queued(signal, songs),
                    precedingArtists = signal.lastArtists,
                    learning = learning,
                )
            RefillBatch(songsBatch, PlaySource.MIX)
        }
    }
}
