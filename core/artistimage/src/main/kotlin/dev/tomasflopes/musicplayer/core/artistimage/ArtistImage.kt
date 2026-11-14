package dev.tomasflopes.musicplayer.core.artistimage

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song

/** The artist as the sources see it: the tag artist's id and name (ADR 0026). */
data class ArtistRef(
    val id: ArtistId,
    val name: String,
)

/** What an artist is shown with. The UI maps each kind to a picture; none of them is ever empty. */
sealed interface ArtistImage {
    /** A photo to download from [url]. */
    data class Remote(
        val url: String,
    ) : ArtistImage

    /** A picture the owner picked, copied to app storage under [fileName]. */
    data class LocalFile(
        val fileName: String,
    ) : ArtistImage

    /** The covers of the artist's albums: one cover, or four tiles for a 2x2. */
    data class Collage(
        val covers: List<Song>,
    ) : ArtistImage {
        init {
            require(covers.size == 1 || covers.size == COLLAGE_TILES) { "A collage has 1 or $COLLAGE_TILES covers" }
        }
    }

    /** Letters on a tile. */
    data class Initials(
        val text: String,
    ) : ArtistImage
}

const val COLLAGE_TILES = 4

/** What one source says about one artist. */
sealed interface SourceResult {
    data class Found(
        val image: ArtistImage,
    ) : SourceResult

    /** This source has nothing; the next one is asked. */
    data object Miss : SourceResult

    /** The owner turned online photos off for this artist: every source with `online = true` is skipped. */
    data object SkipOnline : SourceResult
}

/** One way to get a picture for an artist. Registered with a Hilt `@IntoSet` binding (ADR 0029). */
interface ArtistImageSource {
    /** Lower comes first. Unique across the registered sources. */
    val priority: Int

    /** Whether this source uses the network. */
    val online: Boolean get() = false

    /** Never needs to catch: a source that throws counts as a miss. */
    suspend fun find(artist: ArtistRef): SourceResult
}

/** Where each source sits in the chain; gaps leave room for new sources between them. */
object ArtistImagePriority {
    const val USER_CHOICE = 0
    const val DEEZER = 100
    const val ALBUM_COLLAGE = 200
    const val INITIALS = 300
}

/** Up to two letters for the tile: the first letter or digit of the first two words. `?` when there is none. */
fun initialsOf(name: String): String {
    val letters =
        name
            .split(' ')
            .mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }
            .take(2)
    return if (letters.isEmpty()) "?" else letters.joinToString("").uppercase()
}
