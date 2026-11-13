package dev.tomasflopes.musicplayer.core.search

import dev.tomasflopes.musicplayer.core.model.Album
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.core.model.Song

/** One matched song, with how it matched. */
data class HitSong(
    val song: Song,
    val tier: MatchTier,
    /** Best field that matched: title, artist, album, albumArtist, folder, featured. */
    val field: String,
)

/** Grouped search results, ready to render: a top card then Artists / Albums / Songs sections. */
data class SearchResult(
    val query: String,
    /** The single best match, shown as a playable top card (null when nothing is close enough). */
    val top: HitSong?,
    val artists: List<Artist>,
    val albums: List<Album>,
    val songs: List<HitSong>,
    val hasMore: Boolean,
)
