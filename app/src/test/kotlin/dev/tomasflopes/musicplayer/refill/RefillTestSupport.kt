package dev.tomasflopes.musicplayer.refill

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.mixes.Mix
import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.model.mixes.MixReader
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.fakes.FakeSongReader
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.playback.RefillSignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Sixty songs, each by its own artist with its own title, at position = id in a one-dimensional sound space. */
internal val refillSongs: List<Song> =
    (1L..60L).map { testSong(it, artistId = it, title = "Title $it", artistName = "Artist $it") }

internal fun spaceOf(ids: List<Long> = refillSongs.map { it.id.value }) =
    SoundSpace(ids.toLongArray(), 1, ids.map { it.toFloat() }.toFloatArray())

internal class FakeMixReader(
    mix: Mix?,
) : MixReader {
    val current = MutableStateFlow(mix)

    override fun observeMixes(): Flow<List<Mix>> = flowOf(listOfNotNull(current.value))

    override fun observeMix(id: MixId): Flow<Mix?> = current

    override suspend fun mix(id: MixId): Mix? = current.value?.takeIf { it.id == id }
}

internal class FixedVectors(
    private val space: SoundSpace,
) : SoundVectorSource {
    override suspend fun space(): SoundSpace = space
}

internal class NothingPlayed : PlayedSinceSource {
    override fun observePlayedIdsSince(sinceEpochMs: Long): Flow<Set<SongId>> = flowOf(emptySet())
}

internal fun refillLibrary(songs: List<Song> = refillSongs) =
    RefillLibrary(
        FakeSongReader(songs = songs),
        NothingPlayed(),
        Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC),
    )

internal fun refs(vararg ids: Long) = ids.map { id -> SongRef(SongId(id), ArtistId(id)) }

internal fun signal(
    seed: RefillSeed,
    queued: List<SongRef>,
    nonManualAhead: Int = 0,
    epoch: Int = 1,
    lastArtists: List<ArtistId> = emptyList(),
) = RefillSignal(seed, nonManualAhead, queued, lastArtists, epoch)
