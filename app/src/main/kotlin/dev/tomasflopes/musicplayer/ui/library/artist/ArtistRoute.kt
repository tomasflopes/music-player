package dev.tomasflopes.musicplayer.ui.library.artist

import dev.tomasflopes.musicplayer.core.model.ArtistId

/** The artist page: a plain string route with a numeric id, like the dynamic list's (ADR 0016). */
object ArtistRoute {
    const val ARG_ID = "id"
    const val PATTERN = "artist/{$ARG_ID}"

    fun create(id: ArtistId) = "artist/${id.value}"
}
