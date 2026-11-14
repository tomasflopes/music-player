package dev.tomasflopes.musicplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.playback.queue.QueueEntry
import dev.tomasflopes.musicplayer.playback.queue.QueueWindowSync

internal class ExoPlayerItems(
    private val player: ExoPlayer,
    private val cache: Map<SongId, Song>,
    private val mapper: MediaItemMapper,
) : QueueWindowSync.PlayerItems {
    override val count get() = player.mediaItemCount
    override val index get() = player.currentMediaItemIndex

    override fun setAll(
        refs: List<SongRef>,
        startIndex: Int,
    ) = player.setMediaItems(refs.map(::item), startIndex, 0L)

    override fun append(refs: List<SongRef>) = player.addMediaItems(refs.map(::item))

    override fun removeRange(
        from: Int,
        to: Int,
    ) = player.removeMediaItems(from, to)

    override fun removeFirst(count: Int) = player.removeMediaItems(0, count)

    override fun prepend(refs: List<SongRef>) = player.addMediaItems(0, refs.map(::item))

    private fun item(ref: SongRef): MediaItem {
        val song = checkNotNull(cache[ref.songId]) { "song ${ref.songId.value} was not loaded" }
        return mapper.toMediaItem(QueueEntry(ref, PlaySource.SONGS, manual = false), song)
    }
}
