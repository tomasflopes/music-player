package dev.tomasflopes.musicplayer.ui.library.album

import dev.tomasflopes.musicplayer.core.model.AlbumId

/** The album page: a plain string route with a numeric id, like the dynamic list's (ADR 0016). */
object AlbumRoute {
    const val ARG_ID = "id"
    const val PATTERN = "album/{$ARG_ID}"

    fun create(id: AlbumId) = "album/${id.value}"
}
