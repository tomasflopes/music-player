package dev.tomasflopes.musicplayer.core.search

import dev.tomasflopes.musicplayer.core.model.log.SearchItemType

/** A result ready to render, with the domain object needed to play it or open its page. */
sealed interface SearchHit {
    val type: SearchItemType
    val tier: MatchTier

    /** The best field that matched, for debugging and tests. */
    val field: SearchField
}

data class ArtistHit(
    val artist: dev.tomasflopes.musicplayer.core.model.Artist,
    override val tier: MatchTier,
    override val field: SearchField,
) : SearchHit {
    override val type: SearchItemType get() = SearchItemType.ARTIST
}

data class AlbumHit(
    val album: dev.tomasflopes.musicplayer.core.model.Album,
    override val tier: MatchTier,
    override val field: SearchField,
) : SearchHit {
    override val type: SearchItemType get() = SearchItemType.ALBUM
}

data class SongHit(
    val song: dev.tomasflopes.musicplayer.core.model.Song,
    override val tier: MatchTier,
    override val field: SearchField,
) : SearchHit {
    override val type: SearchItemType get() = SearchItemType.SONG
}
