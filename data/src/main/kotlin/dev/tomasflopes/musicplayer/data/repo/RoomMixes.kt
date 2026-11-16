package dev.tomasflopes.musicplayer.data.repo

import androidx.room.withTransaction
import dev.tomasflopes.musicplayer.core.model.mixes.Mix
import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.model.mixes.MixReader
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeed
import dev.tomasflopes.musicplayer.core.model.mixes.MixSeedKind
import dev.tomasflopes.musicplayer.core.model.mixes.MixStore
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.entity.MixEntity
import dev.tomasflopes.musicplayer.data.db.entity.MixSeedEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private fun MixSeedEntity.toSeed() = MixSeed(MixSeedKind.fromWire(kind), refId)

private fun MixEntity.toMix(seeds: List<MixSeedEntity>) =
    Mix(
        id = MixId(id),
        name = name,
        seeds = seeds.map(MixSeedEntity::toSeed),
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = updatedAt,
    )

private fun seedRows(
    id: Long,
    seeds: List<MixSeed>,
) = seeds.mapIndexed { position, seed -> MixSeedEntity(id, position, seed.kind.wire, seed.refId) }

class RoomMixReader(
    private val db: MusicDatabase,
) : MixReader {
    private val dao get() = db.mixDao()

    override fun observeMixes(): Flow<List<Mix>> =
        combine(dao.observeAll(), dao.observeVisibleSeeds()) { mixes, seeds ->
            val byMix = seeds.groupBy { it.mixId }
            mixes.map { it.toMix(byMix[it.id].orEmpty()) }
        }

    override fun observeMix(id: MixId): Flow<Mix?> = observeMixes().map { mixes -> mixes.firstOrNull { it.id == id } }

    override suspend fun mix(id: MixId): Mix? = dao.get(id.value)?.toMix(dao.visibleSeeds(id.value))
}

class RoomMixStore(
    private val db: MusicDatabase,
) : MixStore {
    private val dao get() = db.mixDao()

    override suspend fun create(
        name: String,
        seeds: List<MixSeed>,
        nowEpochMs: Long,
    ): MixId =
        db.withTransaction {
            val id = dao.insert(MixEntity(name = name, createdAt = nowEpochMs, updatedAt = nowEpochMs))
            dao.insertSeeds(seedRows(id, seeds))
            MixId(id)
        }

    override suspend fun rename(
        id: MixId,
        name: String,
        nowEpochMs: Long,
    ) = dao.rename(id.value, name, nowEpochMs)

    override suspend fun replaceSeeds(
        id: MixId,
        seeds: List<MixSeed>,
        nowEpochMs: Long,
    ) = db.withTransaction {
        dao.deleteVisibleSeeds(id.value)
        dao.insertSeeds(seedRows(id.value, seeds))
        dao.touch(id.value, nowEpochMs)
    }

    override suspend fun delete(id: MixId) = dao.delete(id.value)
}
