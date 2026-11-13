package dev.tomasflopes.musicplayer.ui.library

import dev.tomasflopes.musicplayer.core.model.Section
import dev.tomasflopes.musicplayer.core.model.SectionIndexer
import dev.tomasflopes.musicplayer.core.model.SortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** A library list in its sorted order and the A-Z [sections] over it, built together from one library snapshot. */
data class IndexedList<T>(
    val items: List<T>,
    val sections: List<Section>,
)

/**
 * Indexes every snapshot once, off the main thread, so scrolling and dragging the rail only read the result. The
 * source must already be in [SortKey] order (the readers sort in SQL); [sortText] is the title or name it sorts by.
 */
internal fun <T> Flow<List<T>>.indexed(sortText: (T) -> String): Flow<IndexedList<T>> =
    map { items -> IndexedList(items, SectionIndexer.sections(items.map { SortKey.of(sortText(it)) })) }
        .flowOn(Dispatchers.Default)
