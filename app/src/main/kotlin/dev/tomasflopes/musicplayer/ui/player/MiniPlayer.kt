package dev.tomasflopes.musicplayer.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.playback.PlaybackState
import dev.tomasflopes.musicplayer.playback.queue.RepeatMode
import dev.tomasflopes.musicplayer.ui.components.AlbumArt
import dev.tomasflopes.musicplayer.ui.theme.ArtSize
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** The bar above the tabs; draws nothing while no song is loaded. */
@Composable
fun MiniPlayer(
    state: PlaybackState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.current ?: return
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            LinearProgressIndicator(
                progress = {
                    if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(song, ArtSize.mini)
                TrackText(song, Modifier.weight(1f).padding(horizontal = Spacing.xs))
                Controls(state, onPrevious, onPlayPause, onNext)
            }
        }
    }
}

@Composable
private fun TrackText(
    song: Song,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Controls(
    state: PlaybackState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    IconButton(onPrevious, enabled = state.hasPrevious) {
        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
    }
    IconButton(onPlayPause) {
        if (state.isPlaying) {
            Icon(Icons.Default.Pause, contentDescription = "Pause")
        } else {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
    }
    IconButton(onNext, enabled = state.hasNext || state.repeatMode != RepeatMode.OFF) {
        Icon(Icons.Default.SkipNext, contentDescription = "Next")
    }
}
