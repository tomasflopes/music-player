package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private const val RESET_AFTER_REMOVE_MS = 400L

/**
 * A row that is removed by swiping it either way: the one swipe behaviour of playlists and the queue (ADR 0046).
 * [onRemove] runs once the swipe is confirmed. If the row is still on screen shortly after (the removal was refused,
 * or the next row took over this slot), it comes back to rest, so a slot never stays swiped away. A change of
 * [resetKey] also brings it back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToRemove(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val latestRemove by rememberUpdatedState(onRemove)
    var removals by remember { mutableIntStateOf(0) }
    val swipe =
        rememberSwipeToDismissBoxState(
            confirmValueChange = {
                if (it != SwipeToDismissBoxValue.Settled) {
                    removals++
                    latestRemove()
                }
                it != SwipeToDismissBoxValue.Settled
            },
        )
    LaunchedEffect(resetKey, removals) {
        if (removals > 0) delay(RESET_AFTER_REMOVE_MS)
        swipe.snapTo(SwipeToDismissBoxValue.Settled)
    }
    SwipeToDismissBox(state = swipe, modifier = modifier, backgroundContent = {}) { content() }
}
