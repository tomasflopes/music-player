package dev.tomasflopes.musicplayer.core.model

private const val DEFAULT_DISC = 1

/** The disc a song is on: its tag, or disc 1 when the tag is missing. The one rule for sorting and disc headers. */
val Song.disc: Int get() = discNumber ?: DEFAULT_DISC

/**
 * Album order, exactly as tagged: disc (missing = disc 1), then track; on each disc, numbered tracks come
 * first and the rest follow in [NaturalOrder] of their file names. Ties end on the path, so the order never
 * depends on the order songs arrived in.
 */
object AlbumOrder : Comparator<Song> {
    private val order =
        compareBy<Song> { it.disc }
            .thenBy { it.trackNumber == null }
            .thenBy { it.trackNumber }
            .thenBy(NaturalOrder) { it.fileName }
            .thenBy { it.path }
            .thenBy { it.id.value }

    override fun compare(
        a: Song,
        b: Song,
    ): Int = order.compare(a, b)
}
