package dev.tomasflopes.musicplayer.core.taste.config

/**
 * Every tunable number behind playlist suggestions (design 7.13, ADR 0051), in one place like [TasteConfig]. A
 * filter's points add up; a song is shown only when at least one relevance check gave points and the total is above
 * zero.
 */
data class SuggestionConfig(
    /** How many suggestions the owner sees at a time. */
    val size: Int = 10,
    /** Songs a playlist needs before its page suggests anything. */
    val minSongsOnPage: Int = 1,
    /** Songs a new playlist needs before the picker suggests anything ("once 2-3 songs are added"). */
    val minSongsInPicker: Int = 2,
    val artist: ArtistLinkRule = ArtistLinkRule(),
    val album: AlbumLinkRule = AlbumLinkRule(),
    val taste: TasteRankRule = TasteRankRule(),
    val dismissed: DismissedRule = DismissedRule(),
) {
    init {
        require(size >= 1) { "size must be at least 1: $size" }
        require(minSongsOnPage >= 1) { "minSongsOnPage must be at least 1: $minSongsOnPage" }
        require(minSongsInPicker >= 1) { "minSongsInPicker must be at least 1: $minSongsInPicker" }
    }
}

/** A suggestion shares an artist with a playlist song: the same main artist, or a featured link either way. */
data class ArtistLinkRule(
    val main: Double = 3.0,
    val featured: Double = 2.0,
) {
    init {
        require(main.isFinite() && main >= 0) { "main must not be negative: $main" }
        require(featured.isFinite() && featured >= 0) { "featured must not be negative: $featured" }
    }
}

/** A suggestion is on the same album as a playlist song. */
data class AlbumLinkRule(
    val weight: Double = 2.0,
) {
    init {
        require(weight.isFinite() && weight >= 0) { "weight must not be negative: $weight" }
    }
}

/**
 * Taste ranks the suggestions, it never qualifies one. The song's score and its main artist's score each go through
 * `tanh(score / scale)`, so a few big scores cannot drown the relevance checks: a song is worth at most [song]
 * points and an artist at most [artist], either way up or down.
 */
data class TasteRankRule(
    val song: Double = 1.5,
    val artist: Double = 1.0,
    val scale: Double = 6.0,
) {
    init {
        require(song.isFinite() && song >= 0) { "song must not be negative: $song" }
        require(artist.isFinite() && artist >= 0) { "artist must not be negative: $artist" }
        require(scale.isFinite() && scale > 0) { "scale must be positive: $scale" }
    }
}

/**
 * Dismissing a song pushes down what is like it, for that playlist only: each dismissed song by the same main artist
 * costs [artist] points and each on the same album [album], counting at most [maxCount] dismissed songs of each.
 */
data class DismissedRule(
    val artist: Double = 1.5,
    val album: Double = 1.0,
    val maxCount: Int = 3,
) {
    init {
        require(artist.isFinite() && artist >= 0) { "artist must not be negative: $artist" }
        require(album.isFinite() && album >= 0) { "album must not be negative: $album" }
        require(maxCount >= 1) { "maxCount must be at least 1: $maxCount" }
    }
}
