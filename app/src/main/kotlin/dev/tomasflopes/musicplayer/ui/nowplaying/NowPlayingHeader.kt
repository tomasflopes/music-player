package dev.tomasflopes.musicplayer.ui.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.playback.PlaybackState
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import dev.tomasflopes.musicplayer.ui.components.AlbumArt
import dev.tomasflopes.musicplayer.ui.theme.ArtSize
import dev.tomasflopes.musicplayer.ui.theme.Spacing

private val PlayButtonSize = 72.dp
private val PlayIconSize = 40.dp

/** The large cover, the song's tags, the seek bar and the transport row. */
@Composable
internal fun NowPlayingHeader(
    state: PlaybackState,
    song: Song,
    actions: NowPlayingActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlbumArt(song, ArtSize.hero)
        SongText(song, Modifier.padding(top = Spacing.l))
        SeekBar(state.positionMs, state.durationMs, actions.onSeek, Modifier.padding(top = Spacing.s))
        TransportRow(state, actions)
    }
}

@Composable
private fun SongText(
    song: Song,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = song.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        song.albumTitle?.takeIf { it.isNotBlank() }?.let { album ->
            Text(
                text = album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Shuffle, previous, play / pause, next, repeat. Shuffle and repeat light up in the accent colour while on. */
@Composable
private fun TransportRow(
    state: PlaybackState,
    actions: NowPlayingActions,
) {
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(actions.onToggleShuffle) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = if (state.shuffle) "Shuffle on" else "Shuffle off",
                tint = if (state.shuffle) active else idle,
            )
        }
        IconButton(actions.onPrevious, enabled = state.hasPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
        }
        PlayPauseButton(state.isPlaying, actions.onPlayPause)
        IconButton(actions.onNext, enabled = state.hasNext || state.repeatMode != RepeatMode.OFF) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next")
        }
        IconButton(actions.onCycleRepeat) { RepeatIcon(state.repeatMode, active, idle) }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val (icon, description) = if (isPlaying) Icons.Default.Pause to "Pause" else Icons.Default.PlayArrow to "Play"
    FilledIconButton(onClick, modifier = Modifier.size(PlayButtonSize)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(PlayIconSize))
    }
}

@Composable
private fun RepeatIcon(
    mode: RepeatMode,
    active: Color,
    idle: Color,
) {
    when (mode) {
        RepeatMode.OFF -> Icon(Icons.Default.Repeat, contentDescription = "Repeat off", tint = idle)
        RepeatMode.ALL -> Icon(Icons.Default.Repeat, contentDescription = "Repeat all", tint = active)
        RepeatMode.ONE -> Icon(Icons.Default.RepeatOne, contentDescription = "Repeat one", tint = active)
    }
}
