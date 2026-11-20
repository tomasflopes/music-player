package dev.tomasflopes.musicplayer.refill

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.playback.RefillSignal
import kotlinx.coroutines.flow.first
import java.time.Clock

private const val MS_PER_DAY = 86_400_000L

/** What a refill reads, shared by the sources: every call must be made from `Dispatchers.Default` (finding D3). */
internal class RefillLibrary(
    private val songs: SongReader,
    private val played: PlayedSinceSource,
    private val clock: Clock,
) {
    suspend fun library(): List<Song> = songs.observeSongs().first()

    suspend fun recentlyPlayed(recentDays: Int): Set<SongId> =
        played.observePlayedIdsSince(clock.millis() - recentDays * MS_PER_DAY).first()

    /** The songs already queued, as library songs, so copies of them are recognised by their duplicate key too. */
    fun queued(
        signal: RefillSignal,
        library: List<Song>,
    ): List<Song> {
        val ids = signal.queued.mapTo(HashSet()) { it.songId }
        return library.filter { it.id in ids }
    }
}

/** True when the sound space has a point for [songId]. */
internal fun SoundSpace.has(songId: Long): Boolean = indexOf(songId) >= 0
