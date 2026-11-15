package dev.tomasflopes.musicplayer.data.analysis

import androidx.room.withTransaction
import dev.tomasflopes.musicplayer.core.analysis.AnalysisBatchRunner
import dev.tomasflopes.musicplayer.core.analysis.AnalysisQueue
import dev.tomasflopes.musicplayer.core.analysis.AnalysisStore
import dev.tomasflopes.musicplayer.core.analysis.AnalysisTarget
import dev.tomasflopes.musicplayer.core.analysis.ProfileBlobCodec
import dev.tomasflopes.musicplayer.core.analysis.SoundProfile
import dev.tomasflopes.musicplayer.core.analysis.SoundProfiler
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import dev.tomasflopes.musicplayer.data.db.entity.AnalysisFailureEntity
import dev.tomasflopes.musicplayer.data.db.entity.SoundProfileEntity
import dev.tomasflopes.musicplayer.data.db.entity.mediaStoreContentUri
import javax.inject.Inject

/** The phone queue as the query of ADR 0058; there is no queue table to keep in step. */
class RoomAnalysisQueue
    @Inject
    constructor(
        private val db: MusicDatabase,
        private val profiler: SoundProfiler,
    ) : AnalysisQueue {
        override suspend fun next(
            limit: Int,
            skip: Set<Long>,
        ): List<AnalysisTarget> =
            db
                .soundProfileDao()
                .nextQueued(profiler.version, AnalysisBatchRunner.MAX_ATTEMPTS, skip.toList(), limit)
                .map {
                    AnalysisTarget(
                        it.id,
                        mediaStoreContentUri(it.volumeName, it.mediaStoreId),
                        it.fileSize,
                        it.attempts,
                    )
                }
    }

/** Writes phone results: a profile (source `phone`, the song row's file size) or one more failed attempt. */
class RoomAnalysisStore
    @Inject
    constructor(
        private val db: MusicDatabase,
        private val profiler: SoundProfiler,
    ) : AnalysisStore {
        override suspend fun saveProfile(
            target: AnalysisTarget,
            profile: SoundProfile,
        ) {
            val row =
                SoundProfileEntity(
                    songId = target.songId,
                    profilerVersion = profiler.version,
                    features = ProfileBlobCodec.encode(profile),
                    source = ProfileSource.PHONE,
                    fileSize = target.fileSize,
                    analysedAt = System.currentTimeMillis(),
                )
            val dao = db.soundProfileDao()
            db.withTransaction {
                dao.upsert(row)
                dao.deleteFailures(target.songId)
            }
        }

        override suspend fun recordFailure(
            target: AnalysisTarget,
            message: String,
        ) {
            val dao = db.soundProfileDao()
            db.withTransaction {
                val attempts = (dao.failedAttempts(target.songId, profiler.version) ?: 0) + 1
                dao.upsertFailure(
                    AnalysisFailureEntity(target.songId, profiler.version, attempts, message.take(MAX_MESSAGE)),
                )
            }
        }

        private companion object {
            const val MAX_MESSAGE = 300
        }
    }
