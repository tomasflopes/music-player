package dev.tomasflopes.musicplayer.core.model.mixes

import java.time.Clock

/** The one rule for a seed list: distinct, in order, between [MixLimits.MIN_SEEDS] and [MixLimits.MAX_SEEDS]. */
internal fun cleanSeeds(
    seeds: List<MixSeed>,
    minSeeds: Int,
): List<MixSeed>? {
    val distinct = seeds.distinct()
    val allowed = maxOf(minSeeds, MixLimits.MIN_SEEDS)..MixLimits.MAX_SEEDS
    return distinct.takeIf { it.size in allowed }
}

/** A new mix. The create flow asks for [MixLimits.CREATE_MIN_SEEDS] to [MixLimits.MAX_SEEDS] seeds. */
class CreateMix(
    private val store: MixStore,
    private val clock: Clock,
) {
    sealed interface Result {
        data class Created(
            val id: MixId,
        ) : Result

        data object InvalidName : Result

        /** The seed count is outside the allowed range. */
        data object InvalidSeeds : Result
    }

    suspend operator fun invoke(
        name: String,
        seeds: List<MixSeed>,
        minSeeds: Int = MixLimits.CREATE_MIN_SEEDS,
    ): Result {
        val clean = MixName.clean(name)
        val valid = cleanSeeds(seeds, minSeeds)
        return when {
            clean == null -> Result.InvalidName
            valid == null -> Result.InvalidSeeds
            else -> Result.Created(store.create(clean, valid, clock.millis()))
        }
    }
}

/** Renames a mix and replaces its seeds together; nothing changes unless both are valid. */
class UpdateMix(
    private val store: MixStore,
    private val clock: Clock,
) {
    enum class Result { UPDATED, INVALID_NAME, INVALID_SEEDS }

    suspend operator fun invoke(
        id: MixId,
        name: String,
        seeds: List<MixSeed>,
        minSeeds: Int = MixLimits.MIN_SEEDS,
    ): Result {
        val clean = MixName.clean(name)
        val valid = cleanSeeds(seeds, minSeeds)
        return when {
            clean == null -> Result.INVALID_NAME
            valid == null -> Result.INVALID_SEEDS
            else -> {
                val now = clock.millis()
                store.rename(id, clean, now)
                store.replaceSeeds(id, valid, now)
                Result.UPDATED
            }
        }
    }
}

class DeleteMix(
    private val store: MixStore,
) {
    suspend operator fun invoke(id: MixId) = store.delete(id)
}
