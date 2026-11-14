package dev.tomasflopes.musicplayer.core.taste.score

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongArtists
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType
import dev.tomasflopes.musicplayer.core.taste.config.TasteConfig

/**
 * The global taste score of every song in a stretch of the listening log (design §7.4). Pure: the result depends
 * only on the rows and on `now`, so changing a weight or the half-life and scoring again is the whole "rebuild".
 */
class TasteScorer(
    config: TasteConfig = TasteConfig(),
) {
    private val rules = SignalRules(config.weights)
    private val bursts = BrowsingBursts(config.burst)
    private val decay = Decay(config.decay)
    private val softening = config.burst.softening
    private val weights = config.weights
    private val artistRule = config.artist
    private val searchRule = config.search
    private val shuffleStarts = ArtistShuffleStarts(config.artist)

    /** [events] oldest first, as logged. Every song with a row is returned, best first. */
    fun score(
        events: List<PlayEvent>,
        nowEpochMs: Long,
    ): List<SongScore> = scoreSongs(TasteSignals(plays = events), nowEpochMs)

    /**
     * Songs and artists from all three logs. An artist's score is what its songs earned (the main artist all of it,
     * a featured artist [ArtistRule.featuredShare] of it) plus the signals that belong to the artist alone: artist
     * shuffles started and artist search results. [artists] maps songs to their artists; unknown songs add nothing.
     */
    fun scoreAll(
        signals: TasteSignals,
        artists: Map<SongId, SongArtists>,
        nowEpochMs: Long,
    ): TasteScores {
        val songs = scoreSongs(signals, nowEpochMs)
        val tallies = HashMap<ArtistId, ArtistTally>()

        fun add(
            artist: ArtistId,
            amount: Double,
            at: Long,
        ) = tallies.getOrPut(artist) { ArtistTally() }.add(amount, at)
        songs.forEach { song ->
            val of = artists[song.songId] ?: return@forEach
            add(of.main, song.score, song.lastPlayedAtEpochMs)
            of.featured.filter { it != of.main }.distinct().forEach {
                add(it, song.score * artistRule.featuredShare, song.lastPlayedAtEpochMs)
            }
        }
        shuffleStarts.find(signals.plays, artists).forEach {
            add(it.artistId, artistRule.shuffleStarted * decay.factor(nowEpochMs - it.atEpochMs), it.atEpochMs)
        }
        signals.searches.filter { it.chosenType == SearchItemType.ARTIST && searchRule.artist != 0.0 }.forEach {
            add(ArtistId(it.chosenId), searchRule.artist * decay.factor(nowEpochMs - it.atEpochMs), it.atEpochMs)
        }
        val ranked =
            tallies
                .map { (id, tally) -> ArtistScore(id, tally.score, tally.lastSignalAtEpochMs) }
                .sortedWith(ARTIST_RANKING)
        return TasteScores(songs, ranked)
    }

    private fun scoreSongs(
        signals: TasteSignals,
        nowEpochMs: Long,
    ): List<SongScore> {
        val events = signals.plays
        val outcomes = events.map(rules::outcomeOf)
        val softened = bursts.softened(events, outcomes)
        val tallies = HashMap<SongId, Tally>()
        events.forEachIndexed { index, event ->
            val tally = tallies.getOrPut(event.songId) { Tally() }
            val factor = (if (softened[index]) softening else 1.0) * decay.factor(nowEpochMs - event.startedAtEpochMs)
            // A completed play forgives the skips before it (design §7.4); the +1 is added after. The start bonus
            // and the end weight are tallied apart, so the -1 of a picked song skipped mid-way is forgiven too.
            if (outcomes[index] == PlayOutcome.COMPLETED) tally.negative = 0.0
            tally.add(rules.startBonusOf(event, outcomes[index]) * factor)
            tally.add(rules.endWeightOf(event, outcomes[index]) * factor)
            tally.lastPlayedAtEpochMs = maxOf(tally.lastPlayedAtEpochMs, event.startedAtEpochMs)
        }
        signals.playlistAdds.forEach { add ->
            tallies
                .getOrPut(add.songId) { Tally() }
                .add(weights.addedToPlaylist * decay.factor(nowEpochMs - add.atEpochMs))
        }
        if (searchRule.song != 0.0) {
            signals.searches.filter { it.chosenType == SearchItemType.SONG }.forEach {
                tallies
                    .getOrPut(SongId(it.chosenId)) { Tally() }
                    .add(searchRule.song * decay.factor(nowEpochMs - it.atEpochMs))
            }
        }
        return tallies
            .map { (songId, tally) -> SongScore(songId, tally.positive + tally.negative, tally.lastPlayedAtEpochMs) }
            .sortedWith(RANKING)
    }

    private class Tally(
        var positive: Double = 0.0,
        var negative: Double = 0.0,
        var lastPlayedAtEpochMs: Long = Long.MIN_VALUE,
    ) {
        fun add(contribution: Double) {
            if (contribution < 0) negative += contribution else positive += contribution
        }
    }

    private class ArtistTally(
        var score: Double = 0.0,
        var lastSignalAtEpochMs: Long = Long.MIN_VALUE,
    ) {
        fun add(
            amount: Double,
            at: Long,
        ) {
            score += amount
            lastSignalAtEpochMs = maxOf(lastSignalAtEpochMs, at)
        }
    }

    private companion object {
        val ARTIST_RANKING =
            compareByDescending<ArtistScore> { it.score }
                .thenByDescending { it.lastSignalAtEpochMs }
                .thenBy { it.artistId.value }
        val RANKING =
            compareByDescending<SongScore> { it.score }
                .thenByDescending { it.lastPlayedAtEpochMs }
                .thenBy { it.songId.value }
    }
}
