package dev.tomasflopes.musicplayer.core.model

/**
 * The songs an artist shuffle draws from (design 7.11): the artist's own songs, then the songs that feature them,
 * each song once. Play in order, Play next, Add to queue and album order never use this; they keep the own songs.
 */
fun artistShuffleSongs(
    own: List<Song>,
    featuredOn: List<Song>,
): List<Song> = (own + featuredOn).distinctBy { it.id }
