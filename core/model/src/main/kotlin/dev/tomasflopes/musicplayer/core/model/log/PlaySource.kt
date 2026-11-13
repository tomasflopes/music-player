package dev.tomasflopes.musicplayer.core.model.log

/**
 * Where a play started, as stored in `play_event.source`. [wire] values are permanent: the log is never
 * rewritten, so a value may be added but never renamed. `SONGS` and `ARTIST` are additions to the design (ADR 0008).
 */
enum class PlaySource(
    val wire: String,
) {
    SHUFFLE_ALL("shuffle_all"),
    ARTIST_SHUFFLE("artist_shuffle"),
    SEARCH("search"),
    ALBUM("album"),
    FOLDER("folder"),
    RECENTLY_ADDED("recently_added"),
    MIX("mix"),
    RADIO("radio"),
    DISCOVER("discover"),
    REWIND("rewind"),
    TOP100("top100"),
    PLAYLIST("playlist"),
    QUEUE("queue"),
    SONGS("songs"),
    ARTIST("artist"),
    ;

    /** The label for the same list with shuffle on or off. Only the two paired sources change. */
    fun withShuffle(on: Boolean): PlaySource =
        when (this) {
            ARTIST, ARTIST_SHUFFLE -> if (on) ARTIST_SHUFFLE else ARTIST
            SONGS, SHUFFLE_ALL -> if (on) SHUFFLE_ALL else SONGS
            else -> this
        }

    companion object {
        fun fromWire(wire: String): PlaySource = entries.first { it.wire == wire }
    }
}
