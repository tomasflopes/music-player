package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.Song

/** The order "Add whole artist" adds in: album by album, each in track order, then songs without an album. */
object ArtistSongOrder {
    fun sort(songs: List<Song>): List<Song> = TODO("not yet")
}
