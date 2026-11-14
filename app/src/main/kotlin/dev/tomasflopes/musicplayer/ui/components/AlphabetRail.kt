package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import kotlin.math.roundToInt

/** The width a list leaves free on its right edge for the [AlphabetRail]. */
val AlphabetRailWidth = 24.dp

private val BubbleSize = 56.dp
private val BubbleGap = 12.dp
private const val NO_SLICE = -1

/**
 * The A-Z fast-scroll strip: one letter per entry of [labels], each taking an equal slice of the height. Tapping or
 * dragging over a slice calls [onSelect] with that entry's index, and a bubble with that letter follows the finger
 * to the left of the strip until it lets go. Hidden when there is nothing to jump between.
 */
@Composable
fun AlphabetRail(
    labels: List<Char>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.size < 2) return
    var heightPx by remember { mutableIntStateOf(0) }
    var active by remember { mutableIntStateOf(NO_SLICE) }
    val count by rememberUpdatedState(labels.size)
    val select by rememberUpdatedState(onSelect)

    fun touch(y: Float) {
        val slice = if (heightPx == 0) 0 else (y / heightPx * count).toInt().coerceIn(0, count - 1)
        if (slice != active) {
            active = slice
            select(slice)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .width(AlphabetRailWidth)
                .onSizeChanged { heightPx = it.height }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            touch(offset.y)
                            // A drag takes the gesture over and clears the bubble itself when it ends.
                            if (tryAwaitRelease()) active = NO_SLICE
                        },
                    )
                }.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> touch(offset.y) },
                        onVerticalDrag = { change, _ -> touch(change.position.y) },
                        onDragEnd = { active = NO_SLICE },
                        onDragCancel = { active = NO_SLICE },
                    )
                },
    ) {
        Letters(labels)
        if (active in labels.indices) {
            val centerY = heightPx * (2 * active + 1) / (count * 2).toFloat()
            LetterBubble(labels[active], centerY, railHeightPx = heightPx)
        }
    }
}

@Composable
private fun Letters(labels: List<Char>) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
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

/** The big letter shown to the left of the finger, kept inside the strip's height. */
@Composable
private fun LetterBubble(
    label: Char,
    centerY: Float,
    railHeightPx: Int,
) {
    Surface(
        modifier =
            Modifier
                .size(BubbleSize)
                .offset {
                    val size = BubbleSize.roundToPx()
                    val top = (centerY - size / 2).roundToInt().coerceIn(0, maxOf(railHeightPx - size, 0))
                    IntOffset(x = -(size + BubbleGap.roundToPx()), y = top)
                },
        shape = RoundedCornerShape(Spacing.s),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label.toString(), style = MaterialTheme.typography.headlineMedium)
        }
    }
}
