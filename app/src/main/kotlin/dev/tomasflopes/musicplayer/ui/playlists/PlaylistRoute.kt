package dev.tomasflopes.musicplayer.ui.playlists

import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistId

/** The screen of one playlist: a plain string route with the numeric id, like the album and artist routes. */
object PlaylistRoute {
    const val ARG_ID = "id"
    const val PATTERN = "playlist/{$ARG_ID}"

    fun create(id: PlaylistId) = "playlist/${id.value}"
}

/** "Add songs": pick songs for one playlist from the whole library. */
object PlaylistPickerRoute {
    const val ARG_ID = "id"
    const val PATTERN = "playlist/{$ARG_ID}/add"

    fun create(id: PlaylistId) = "playlist/${id.value}/add"
}

/** After "New playlist": open the empty playlist, then its song picker on top, so Back from the picker lands on it. */
fun ((String) -> Unit).openNewPlaylist(id: PlaylistId) {
    invoke(PlaylistRoute.create(id))
    invoke(PlaylistPickerRoute.create(id))
}
