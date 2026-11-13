package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.taste.shuffle.ShuffleStrategy
import kotlin.random.Random

/** Songs are named by one letter; the id is the letter's code, so failures read as letters. */
internal fun ref(name: Char) = SongRef(SongId(name.code.toLong()), ArtistId(1))

internal fun refs(names: String) = names.map(::ref)

internal val QueueEntry.name: Char get() =
    ref.songId.value
        .toInt()
        .toChar()

internal fun PlayQueue.names(): String = window(behind = size, ahead = size).map { it.name }.joinToString("")

/** Reverses, so every shuffled result is exact. */
internal object ReverseShuffle : ShuffleStrategy {
    override fun order(
        items: List<SongRef>,
        random: Random,
    ) = items.reversed()
}

internal fun queue(
    names: String,
    startIndex: Int = 0,
    source: PlaySource = PlaySource.ALBUM,
    shuffled: Boolean = false,
    strategy: ShuffleStrategy = ReverseShuffle,
): PlayQueue = PlayQueue(strategy, Random(1)).apply { replace(refs(names), startIndex, source, shuffled) }
