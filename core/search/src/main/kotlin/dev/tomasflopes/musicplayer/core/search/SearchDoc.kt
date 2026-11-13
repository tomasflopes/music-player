package dev.tomasflopes.musicplayer.core.search

import dev.tomasflopes.musicplayer.core.model.log.SearchItemType

/** One searchable item. [display] is shown exactly as tagged; [fields] hold the raw values to match. */
data class SearchDoc(
    val type: SearchItemType,
    val id: Long,
    val display: String,
    val fields: Map<SearchField, List<String>>,
) {
    /** [display] folded for ordering ties, once per document instead of once per comparison. */
    val sortKey: String by lazy(LazyThreadSafetyMode.PUBLICATION) { TextNormalizer.spaced(display) }
}
