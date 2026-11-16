package dev.tomasflopes.musicplayer.ui.mixes

import dev.tomasflopes.musicplayer.core.model.mixes.MixId

/** "New mix": the create screen, a plain string route like the playlist routes. */
object NewMixRoute {
    const val PATTERN = "mix/new"
}

/** The edit screen of one mix. */
object EditMixRoute {
    const val ARG_ID = "id"
    const val PATTERN = "mix/{$ARG_ID}/edit"

    fun create(id: MixId) = "mix/${id.value}/edit"
}
