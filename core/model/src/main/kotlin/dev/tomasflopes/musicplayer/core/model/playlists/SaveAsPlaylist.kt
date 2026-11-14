package dev.tomasflopes.musicplayer.core.model.playlists

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.PlaySource

/**
 * "Save as playlist": the songs of a list or of the queue, as a new playlist (design 7.12, ADR 0044).
 * Never Shuffle all (that list is the whole library, not a choice), and at most [MAX_SONGS] songs.
 */
class SaveAsPlaylist(
    private val create: CreatePlaylist,
) {
    sealed interface Result {
        data class Saved(
            val id: PlaylistId,
            val added: Int,
        ) : Result

        data object InvalidName : Result

        data object Empty : Result

        /** The list came from Shuffle all. */
        data object ShuffleAll : Result

        data class TooManySongs(
            val max: Int,
        ) : Result
    }

    /**
     * [sources] are the play sources behind [songs]: one for a list screen, the non-manual entries' sources for the
     * queue. Any [PlaySource.SHUFFLE_ALL] among them refuses the save. A song in [songs] twice goes in once.
     */
    suspend operator fun invoke(
        name: String,
        songs: List<SongId>,
        sources: Collection<PlaySource>,
    ): Result {
        val wanted = songs.distinct()
        return when {
            PlaySource.SHUFFLE_ALL in sources -> Result.ShuffleAll
            wanted.isEmpty() -> Result.Empty
            wanted.size > MAX_SONGS -> Result.TooManySongs(MAX_SONGS)
            else ->
                when (val created = create(name, wanted)) {
                    is CreatePlaylist.Result.Created -> Result.Saved(created.id, created.added)
                    CreatePlaylist.Result.InvalidName -> Result.InvalidName
                }
        }
    }

    companion object {
        const val MAX_SONGS = 500
    }
}
