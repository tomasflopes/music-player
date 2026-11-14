package dev.tomasflopes.musicplayer.core.taste.suggest

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.taste.config.AlbumLinkRule
import dev.tomasflopes.musicplayer.core.taste.config.ArtistLinkRule
import dev.tomasflopes.musicplayer.core.taste.config.DismissedRule
import dev.tomasflopes.musicplayer.core.taste.config.TasteRankRule
import kotlin.math.tanh

/**
 * Same main or featured artist as a playlist song: the same main artist scores [ArtistLinkRule.main]; a featured link
 * either way (the candidate features a playlist artist, or a playlist song features the candidate's artist) scores
 * [ArtistLinkRule.featured]. The better of the two counts, boosted when several playlist songs share the link.
 */
class ArtistLinkFilter(
    private val rule: ArtistLinkRule = ArtistLinkRule(),
) : SuggestionFilter {
    override val id = "artist_link"
    override val isRelevanceCheck = true

    override fun points(
        candidate: Song,
        context: SuggestionContext,
    ): Double {
        val sameMain = context.memberMainArtists[candidate.artistId] ?: 0
        val linked = context.artistIdsOf(candidate).maxOfOrNull { context.memberArtists[it] ?: 0 } ?: 0
        return maxOf(rule.main * repeatBoost(sameMain), rule.featured * repeatBoost(linked))
    }
}

/** On the same album as a playlist song, boosted when several playlist songs are on it. */
class AlbumLinkFilter(
    private val rule: AlbumLinkRule = AlbumLinkRule(),
) : SuggestionFilter {
    override val id = "album_link"
    override val isRelevanceCheck = true

    override fun points(
        candidate: Song,
        context: SuggestionContext,
    ): Double {
        val shared = candidate.albumId?.let { context.memberAlbums[it] } ?: 0
        return rule.weight * repeatBoost(shared)
    }
}

/**
 * Taste ranks, it never qualifies (design 7.13): the song's global score and its main artist's score (ADR 0049),
 * each squashed by `tanh` so they stay within [TasteRankRule.song] and [TasteRankRule.artist] points.
 */
class TasteScoreFilter(
    private val rule: TasteRankRule = TasteRankRule(),
) : SuggestionFilter {
    override val id = "taste_score"
    override val isRelevanceCheck = false

    override fun points(
        candidate: Song,
        context: SuggestionContext,
    ): Double =
        rule.song * tanh(context.songScore(candidate.id) / rule.scale) +
            rule.artist * tanh(context.artistScore(candidate.artistId) / rule.scale)
}

/**
 * What the owner dismissed for this playlist counts against what is like it (ADR 0051): same main artist or same
 * album as a dismissed song. Negative points, for ranking in this playlist only; never a relevance check.
 */
class DismissedNeighbourFilter(
    private val rule: DismissedRule = DismissedRule(),
) : SuggestionFilter {
    override val id = "dismissed_neighbour"
    override val isRelevanceCheck = false

    override fun points(
        candidate: Song,
        context: SuggestionContext,
    ): Double {
        val sameArtist = (context.dismissedMainArtists[candidate.artistId] ?: 0).coerceAtMost(rule.maxCount)
        val sameAlbum = (candidate.albumId?.let { context.dismissedAlbums[it] } ?: 0).coerceAtMost(rule.maxCount)
        return -(rule.artist * sameArtist + rule.album * sameAlbum)
    }
}
