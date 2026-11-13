package dev.tomasflopes.musicplayer.core.model.log

/** A search result the owner chose. Insert-only, like [PlayEvent]. */
data class SearchEvent(
    val query: String,
    val chosenType: SearchItemType,
    val chosenId: Long,
    val atEpochMs: Long,
)
