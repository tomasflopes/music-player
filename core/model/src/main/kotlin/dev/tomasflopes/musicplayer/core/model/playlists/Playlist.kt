package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.SongId

@JvmInline
value class PlaylistId(
    val value: Long,
)

/** A saved, frozen list of songs (design 7.12). The songs are in [PlaylistReader.observeSongs]. */
data class Playlist(
    val id: PlaylistId,
    val name: String,
    /** Songs the owner can see: a retired song is not counted (ADR 0021). */
    val songCount: Int,
    /** The first visible song, whose cover stands for the playlist. */
    val coverSongId: SongId?,
    /** When songs were last added, or when it was created; orders the add sheet. */
    val lastUsedAtEpochMs: Long,
)
