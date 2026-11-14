package dev.tomasflopes.musicplayer.ui.nowplaying

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val TrackHeight = 4.dp
private val ThumbIdle = 6.dp
private val ThumbDragged = 10.dp

/** Tall enough to grab with a thumb, though the track itself is slim. */
private val TouchHeight = 40.dp

/** After a seek the bar stays where it was let go until the player's position catches up. */
private const val HOLD_AFTER_SEEK_MS = 1_000L

/**
 * The position bar of Now Playing: a slim track and a small thumb that grows while it is dragged. A tap or the end
 * of a drag seeks; while the finger is down the bar follows it instead of the player.
 */
@Composable
internal fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    var settling by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(settling) {
        if (settling != null) {
            delay(HOLD_AFTER_SEEK_MS)
            settling = null
        }
    }
    val enabled = durationMs > 0
    val played = if (enabled) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val fraction = dragged ?: settling ?: played
    val shownMs = if (dragged != null || settling != null) (fraction * durationMs).toLong() else positionMs

    Column(modifier.fillMaxWidth()) {
        SeekTrack(
            fraction = fraction,
            dragging = dragged != null,
            enabled = enabled,
            onDrag = { dragged = it },
            onCommit = {
                settling = it
                onSeek((it * durationMs).toLong())
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeLabel(formatPlaybackTime(shownMs))
            TimeLabel(formatPlaybackTime(durationMs))
        }
    }
}

@Composable
private fun SeekTrack(
    fraction: Float,
    dragging: Boolean,
    enabled: Boolean,
    onDrag: (Float?) -> Unit,
    onCommit: (Float) -> Unit,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val thumb by animateDpAsState(if (dragging) ThumbDragged else ThumbIdle, label = "seek thumb")
    val drag by rememberUpdatedState(onDrag)
    val commit by rememberUpdatedState(onCommit)
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.outline

    fun at(x: Float): Float = if (widthPx == 0) 0f else (x / widthPx).coerceIn(0f, 1f)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TouchHeight)
                .onSizeChanged { widthPx = it.width }
                .semantics {
                    contentDescription = "Seek"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                }.drawBehind { drawSeekTrack(fraction, enabled, thumb, played, rest) }
                .pointerInput(enabled) {
                    if (enabled) detectTapGestures { offset -> commit(at(offset.x)) }
                }.pointerInput(enabled) {
                    if (enabled) {
                        var last = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                last = at(offset.x)
                                drag(last)
                            },
                            onHorizontalDrag = { change, _ ->
                                last = at(change.position.x)
                                drag(last)
                            },
                            onDragEnd = {
                                commit(last)
                                drag(null)
                            },
                            onDragCancel = { drag(null) },
                        )
                    }
                },
    )
}

private fun DrawScope.drawSeekTrack(
    fraction: Float,
    enabled: Boolean,
    thumb: Dp,
    played: Color,
    rest: Color,
) {
    val track = TrackHeight.toPx()
    val radius = thumb.toPx()
    val centerY = size.height / 2
    val top = Offset(0f, centerY - track / 2)
    val corner = CornerRadius(track / 2)
    drawRoundRect(rest, top, Size(size.width, track), corner)
    drawRoundRect(played, top, Size(size.width * fraction, track), corner)
    if (enabled) {
        drawCircle(played, radius, Offset((size.width * fraction).coerceIn(radius, size.width - radius), centerY))
    }
}

@Composable
private fun TimeLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
