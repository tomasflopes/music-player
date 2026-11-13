package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/** The width a list leaves free on its right edge for the [AlphabetRail]. */
val AlphabetRailWidth = 24.dp

/**
 * The A-Z fast-scroll strip: one letter per entry of [labels], each taking an equal slice of the height. Tapping or
 * dragging over a slice calls [onSelect] with that entry's index. Hidden when there is nothing to jump between.
 */
@Composable
fun AlphabetRail(
    labels: List<Char>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.size < 2) return
    var heightPx by remember { mutableIntStateOf(0) }
    var lastSelected by remember { mutableIntStateOf(-1) }
    val count by rememberUpdatedState(labels.size)
    val select by rememberUpdatedState(onSelect)

    fun sliceAt(y: Float): Int = if (heightPx == 0) 0 else (y / heightPx * count).toInt().coerceIn(0, count - 1)

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(AlphabetRailWidth)
                .onSizeChanged { heightPx = it.height }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> select(sliceAt(offset.y)) }
                }.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            lastSelected = sliceAt(offset.y)
                            select(lastSelected)
                        },
                        onVerticalDrag = { change, _ ->
                            val slice = sliceAt(change.position.y)
                            if (slice != lastSelected) {
                                lastSelected = slice
                                select(slice)
                            }
                        },
                    )
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        labels.forEach { label ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = label.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
