package dev.tomasflopes.musicplayer.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource

/** The session commands the app's own controller sends to the service; the queue lives in the service. */
internal object PlaybackCommands {
    val play = SessionCommand("musicplayer.PLAY", Bundle.EMPTY)
    val playNext = SessionCommand("musicplayer.PLAY_NEXT", Bundle.EMPTY)
    val addToQueue = SessionCommand("musicplayer.ADD_TO_QUEUE", Bundle.EMPTY)
    val jumpToUpcoming = SessionCommand("musicplayer.JUMP_TO_UPCOMING", Bundle.EMPTY)
    val jumpToQueueIndex = SessionCommand("musicplayer.JUMP_TO_QUEUE_INDEX", Bundle.EMPTY)
    val removeFromQueue = SessionCommand("musicplayer.REMOVE_FROM_QUEUE", Bundle.EMPTY)
    val restoreToQueue = SessionCommand("musicplayer.RESTORE_TO_QUEUE", Bundle.EMPTY)
    val all = listOf(play, playNext, addToQueue, jumpToUpcoming, jumpToQueueIndex, removeFromQueue, restoreToQueue)

    private const val SONG_IDS = "songIds"
    private const val ARTIST_IDS = "artistIds"
    private const val START = "start"
    private const val SHUFFLE = "shuffle"
    private const val SOURCE = "source"
    private const val PICKED = "picked"
    private const val PLAYLIST_ID = "playlistId"
    private const val INDEX = "index"
    private const val SONG_ID = "songId"
    private const val ARTIST_ID = "artistId"
    private const val MANUAL = "manual"
    private const val WAS_CURRENT = "wasCurrent"
    private const val POSITION = "positionMs"
    private const val WAS_PLAYING = "wasPlaying"

    fun bundleOf(refs: List<SongRef>) =
        Bundle().apply {
            putLongArray(SONG_IDS, LongArray(refs.size) { refs[it].songId.value })
            putLongArray(ARTIST_IDS, LongArray(refs.size) { refs[it].mainArtistId.value })
        }

    fun refsOf(bundle: Bundle): List<SongRef> {
        val songs = bundle.getLongArray(SONG_IDS)
        val artists = bundle.getLongArray(ARTIST_IDS)
        if (songs == null || artists == null) return emptyList()
        return songs.indices.map { SongRef(SongId(songs[it]), ArtistId(artists[it])) }
    }

    fun bundleOf(request: PlayRequest) =
        bundleOf(request.songs).apply {
            putInt(START, request.startIndex)
            putBoolean(SHUFFLE, request.shuffle)
            putString(SOURCE, request.source.wire)
            putBoolean(PICKED, request.picked)
            request.playlistId?.let { putLong(PLAYLIST_ID, it) }
        }

    fun requestOf(bundle: Bundle) =
        PlayRequest(
            songs = refsOf(bundle),
            startIndex = bundle.getInt(START),
            shuffle = bundle.getBoolean(SHUFFLE),
            source = PlaySource.fromWire(bundle.getString(SOURCE) ?: PlaySource.SONGS.wire),
            picked = bundle.getBoolean(PICKED),
            playlistId = if (bundle.containsKey(PLAYLIST_ID)) bundle.getLong(PLAYLIST_ID) else null,
        )

    fun bundleOf(index: Int) = Bundle().apply { putInt(INDEX, index) }

    fun indexOf(bundle: Bundle) = bundle.getInt(INDEX)

    /** A queue entry named by place and song, so a stale place is caught by the engine. */
    fun bundleOf(
        index: Int,
        songId: SongId,
    ) = bundleOf(index).apply { putLong(SONG_ID, songId.value) }

    fun songIdOf(bundle: Bundle) = SongId(bundle.getLong(SONG_ID))

    fun bundleOf(removed: RemovedQueueEntry) =
        bundleOf(removed.index, removed.ref.songId).apply {
            putLong(ARTIST_ID, removed.ref.mainArtistId.value)
            putString(SOURCE, removed.source.wire)
            putBoolean(MANUAL, removed.manual)
            removed.playlistId?.let { putLong(PLAYLIST_ID, it) }
            putBoolean(WAS_CURRENT, removed.wasCurrent)
            putLong(POSITION, removed.positionMs)
            putBoolean(WAS_PLAYING, removed.wasPlaying)
        }

    fun removedOf(bundle: Bundle) =
        RemovedQueueEntry(
            index = indexOf(bundle),
            ref = SongRef(songIdOf(bundle), ArtistId(bundle.getLong(ARTIST_ID))),
            source = PlaySource.fromWire(bundle.getString(SOURCE) ?: PlaySource.QUEUE.wire),
            manual = bundle.getBoolean(MANUAL),
            playlistId = if (bundle.containsKey(PLAYLIST_ID)) bundle.getLong(PLAYLIST_ID) else null,
            wasCurrent = bundle.getBoolean(WAS_CURRENT),
            positionMs = bundle.getLong(POSITION),
            wasPlaying = bundle.getBoolean(WAS_PLAYING),
        )
}
