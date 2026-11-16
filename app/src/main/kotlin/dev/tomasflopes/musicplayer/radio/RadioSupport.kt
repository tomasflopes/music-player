package dev.tomasflopes.musicplayer.radio

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.PlayedSinceSource
import dev.tomasflopes.musicplayer.core.model.ref
import dev.tomasflopes.musicplayer.core.taste.config.RadioConfig
import dev.tomasflopes.musicplayer.playback.PlayRequest
import kotlinx.coroutines.flow.first
import java.time.Clock
import kotlin.random.Random

// What the song radio and the artist radio use the same way (ADR 0072, 0075): the play request, the recent window and
// the exploration draw.

private const val MS_PER_DAY = 86_400_000L

/** The batch as one request: replaces the queue, starts at the first song, logged as `radio` and picked. */
internal fun radioRequest(batch: List<Song>) =
    PlayRequest(
        songs = batch.map { it.ref },
        startIndex = 0,
        shuffle = false,
        source = PlaySource.RADIO,
        picked = true,
    )

/** Songs played in the last [RadioConfig.recentDays] days. */
internal suspend fun recentlyPlayed(
    played: PlayedSinceSource,
    clock: Clock,
    config: RadioConfig,
): Set<SongId> = played.observePlayedIdsSince(clock.millis() - config.recentDays * MS_PER_DAY).first()

/** A fixed draw when the config has a seed (tests), a new one for every radio otherwise. */
internal fun radioRandom(config: RadioConfig): Random = config.seed?.let { Random(it) } ?: Random.Default
