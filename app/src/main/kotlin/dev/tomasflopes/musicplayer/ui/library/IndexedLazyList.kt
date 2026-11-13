package dev.tomasflopes.musicplayer.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.ui.components.AlphabetRail
import dev.tomasflopes.musicplayer.ui.components.AlphabetRailWidth
import kotlinx.coroutines.launch

/**
 * A lazy list with stable [key]s and an A-Z rail over [list]'s sections. Rows leave room for the rail on the right;
 * a row gets its position in the list so a tap can say where playback starts.
 */
@Composable
internal fun <T> IndexedLazyList(
    list: IndexedList<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int, item: T) -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val labels = remember(list.sections) { list.sections.map { it.label } }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(end = AlphabetRailWidth),
        ) {
            itemsIndexed(list.items, key = { _, item -> key(item) }) { index, item -> row(index, item) }
        }
        AlphabetRail(
            labels = labels,
            onSelect = { section -> scope.launch { state.scrollToItem(list.sections[section].firstIndex) } },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
