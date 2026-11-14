package dev.tomasflopes.musicplayer.core.artistimage

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow

/** Deezer's artist search. The raw JSON body, or `null` when the request failed for any reason. */
fun interface DeezerClient {
    suspend fun searchArtists(
        query: String,
        limit: Int,
    ): String?
}

/** Whether the tag artist and a Deezer artist are the same name. Implemented with `TextNormalizer` in `:data`. */
fun interface ArtistNameMatcher {
    fun matches(
        tagName: String,
        candidateName: String,
    ): Boolean
}

/** Whether a network request for an artist photo may be made right now (the setting and the connection). */
fun interface OnlinePolicy {
    suspend fun allowed(): Boolean
}

fun interface TimeSource {
    fun nowMs(): Long
}

/** What the last Deezer lookup for an artist found; a `null` [pictureUrl] means it found nothing. */
data class ArtistImageLookup(
    val pictureUrl: String?,
    val checkedAtMs: Long,
)

interface ArtistImageLookupCache {
    suspend fun get(id: ArtistId): ArtistImageLookup?

    suspend fun put(
        id: ArtistId,
        lookup: ArtistImageLookup,
    )
}

/** What the owner chose for one artist. No choice at all means automatic. */
sealed interface ArtistImageChoice {
    /** A Deezer photo picked from the candidates. */
    data class Remote(
        val url: String,
    ) : ArtistImageChoice

    /** A gallery image copied to app storage. */
    data class LocalFile(
        val fileName: String,
    ) : ArtistImageChoice

    /** No online photo for this artist: the collage or the initials. */
    data object Generated : ArtistImageChoice
}

interface ArtistImageOverrideStore {
    suspend fun get(id: ArtistId): ArtistImageChoice?

    suspend fun set(
        id: ArtistId,
        choice: ArtistImageChoice,
    )

    suspend fun clear(id: ArtistId)
}

/** Songs whose embedded art stands for the artist's albums, one per album, at most [limit]. */
fun interface ArtistCovers {
    suspend fun coverSongs(
        id: ArtistId,
        limit: Int,
    ): List<Song>
}

/** Gallery pictures the app copied into its own storage. */
fun interface LocalImageFiles {
    suspend fun delete(fileName: String)
}

/** The setting "Artist photos from the internet". */
enum class ArtistPhotoMode {
    OFF,
    WIFI_ONLY,
    ALWAYS,
    ;

    companion object {
        /** Wi-Fi only: photos show up without spending mobile data. */
        val DEFAULT = WIFI_ONLY
    }
}

interface ArtistPhotoSettings {
    val mode: Flow<ArtistPhotoMode>

    suspend fun setMode(mode: ArtistPhotoMode)
}

enum class NetworkKind { NONE, METERED, UNMETERED }

fun ArtistPhotoMode.allowsRequest(network: NetworkKind): Boolean =
    when (this) {
        ArtistPhotoMode.OFF -> false
        ArtistPhotoMode.WIFI_ONLY -> network == NetworkKind.UNMETERED
        ArtistPhotoMode.ALWAYS -> network != NetworkKind.NONE
    }
