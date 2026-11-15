package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.taste.config.RewindConfig
import dev.tomasflopes.musicplayer.core.taste.score.PastScorer
import dev.tomasflopes.musicplayer.core.taste.score.TasteSignals
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Picks the Rewind list from what the listening log says (design §7.7, ADR 0064). Pure Kotlin.
 *
 * 1. The cutoff is now minus [RewindConfig.quietDays]. Each song is scored as it stood at the cutoff, from the
 *    rows before it only ([PastScorer], the same scorer and decay as today's scores).
 * 2. Candidates are the songs scoring at least [RewindConfig.minScoreThen] then, that are still in the library and
 *    have no play at or after the cutoff.
 * 3. Each gets a rank: its past score squashed with `tanh` plus a small random part (the same seeded hash as
 *    Discover, so a seed gives one list and a song that leaves does not move the others).
 * 4. The best are kept, at most [RewindConfig.maxPerArtist] per main artist, up to [RewindConfig.size], then spread
 *    so no main artist repeats within [RewindConfig.artistGap] songs (the steps Discover uses).
 */
class RewindPolicy(
    private val config: RewindConfig = RewindConfig(),
    private val past: PastScorer = PastScorer(),
) {
    /** Songs played at or after this are "played lately". */
    fun cutoffAt(nowEpochMs: Long): Long = nowEpochMs - config.quietMs

    /** The oldest log row [pick] can use at [nowEpochMs]; read the logs from here. */
    fun logsFrom(nowEpochMs: Long): Long = past.oldestRowFor(cutoffAt(nowEpochMs))

    /**
     * @param library every song in the library; a song that left it cannot be listed.
     * @param playedLately songs with a `play_event` row at or after [cutoffAt], of any outcome.
     * @param signals the three logs from [logsFrom] up to now, oldest first.
     */
    fun pick(
        library: List<Song>,
        playedLately: Set<SongId>,
        signals: TasteSignals,
        nowEpochMs: Long,
        random: Random,
    ): List<Song> {
        val lovedThen =
            past
                .songsAsOf(signals, cutoffAt(nowEpochMs))
                .filter { it.score >= config.minScoreThen }
                .associate { it.songId to it.score }
        val salt = random.nextLong()
        val ranked =
            library
                .filter { it.id in lovedThen && it.id !in playedLately }
                .map { Ranked(it, rank(it.id, lovedThen.getValue(it.id), salt)) }
                .sortedWith(compareByDescending<Ranked> { it.rank }.thenBy { it.song.id.value })
        val best = ranked.map { it.song }.cappedPerArtist(config.maxPerArtist, config.size)
        return best.spreadByArtist(config.artistGap)
    }

    private class Ranked(
        val song: Song,
        val rank: Double,
    )

    private fun rank(
        id: SongId,
        scoreThen: Double,
        salt: Long,
    ): Double = tanh(scoreThen / config.tasteScale) + config.randomWeight * seededUnit(salt, id.value)
}
