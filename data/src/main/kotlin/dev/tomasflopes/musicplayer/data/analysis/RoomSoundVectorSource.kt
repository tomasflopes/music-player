package dev.tomasflopes.musicplayer.data.analysis

import dev.tomasflopes.musicplayer.core.analysis.ProfileBlobCodec
import dev.tomasflopes.musicplayer.core.analysis.RawSoundProfile
import dev.tomasflopes.musicplayer.core.analysis.SoundProfiler
import dev.tomasflopes.musicplayer.core.analysis.SoundVectorizer
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.data.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The sound space over Room (ADR 0069). One query reads only `song_id` and the first 132 bytes of each current
 * profile of an in-library song (ADR 0058); the vectorizer normalises over exactly those songs. The space is built on
 * the first call and kept until the process ends, so profiles imported or analysed meanwhile show up at the next
 * app start.
 */
@Singleton
class RoomSoundVectorSource
    @Inject
    constructor(
        private val db: MusicDatabase,
        private val profiler: SoundProfiler,
    ) : SoundVectorSource {
        private val vectorizer = SoundVectorizer()
        private val lock = Mutex()

        @Volatile
        private var cached: SoundSpace? = null

        override suspend fun space(): SoundSpace = cached ?: lock.withLock { cached ?: build().also { cached = it } }

        private suspend fun build(): SoundSpace {
            val rows = db.soundProfileDao().soundHeads(profiler.version)
            return withContext(Dispatchers.Default) {
                vectorizer.vectorize(rows.map { RawSoundProfile(it.songId, ProfileBlobCodec.decodeFixed(it.head)) })
            }
        }
    }
