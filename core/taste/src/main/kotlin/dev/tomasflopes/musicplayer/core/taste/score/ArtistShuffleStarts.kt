package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongArtists
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.StartedBy
import dev.tomasflopes.musicplayer.core.taste.config.ArtistRule

/** An artist shuffle the owner started, as read from the log: the artist and when it began. */
data class ArtistShuffleStart(
    val artistId: ArtistId,
    val atEpochMs: Long,
)

/**
 * Finds artist shuffles in the log. The log has no artist for a shuffle, so a shuffle is a run of consecutive
 * `ARTIST_SHUFFLE` rows (starts at most [ArtistRule.shuffleGapMs] apart) whose first row was picked, and its
 * artist is the main artist most of its songs belong to (ties go to the one heard first). Songs of other main
 * artists in the queue, which artist shuffle adds when they feature the artist (ADR 0027), are outvoted.
 */
class ArtistShuffleStarts(
    private val rule: ArtistRule = ArtistRule(),
) {
    /** [plays] oldest first, as logged. */
    fun find(
        plays: List<PlayEvent>,
        artists: Map<SongId, SongArtists>,
    ): List<ArtistShuffleStart> = runsOf(plays).mapNotNull { run -> startOf(run, artists) }

    private fun runsOf(plays: List<PlayEvent>): List<List<PlayEvent>> {
        val runs = mutableListOf<MutableList<PlayEvent>>()
        var current: MutableList<PlayEvent>? = null
        plays.forEach { play ->
            val last = current?.last()
            val continues =
                play.source == PlaySource.ARTIST_SHUFFLE &&
                    last != null &&
                    play.startedAtEpochMs - last.startedAtEpochMs <= rule.shuffleGapMs
            when {
                continues -> current!!.add(play)
                play.source == PlaySource.ARTIST_SHUFFLE -> current = mutableListOf(play).also { runs += it }
                else -> current = null
            }
        }
        return runs
    }

    private fun startOf(
        run: List<PlayEvent>,
        artists: Map<SongId, SongArtists>,
    ): ArtistShuffleStart? {
        val first = run.first()
        if (first.startedBy != StartedBy.PICKED) return null
        val mains = run.mapNotNull { artists[it.songId]?.main }
        val winner =
            mains.groupingBy { it }.eachCount().maxWithOrNull(
                compareBy({ it.value }, { -mains.indexOf(it.key) }),
            )
        return winner?.let { ArtistShuffleStart(it.key, first.startedAtEpochMs) }
    }
}
