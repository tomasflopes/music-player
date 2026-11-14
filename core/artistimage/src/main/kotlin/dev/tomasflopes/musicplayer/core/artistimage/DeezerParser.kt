package dev.tomasflopes.musicplayer.core.artistimage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One artist of a Deezer search that has a real photo. */
data class DeezerHit(
    val name: String,
    val pictureUrl: String,
)

sealed interface DeezerParse {
    /** A valid reply; [hits] can be empty. Hits whose picture is Deezer's placeholder are already left out. */
    data class Hits(
        val hits: List<DeezerHit>,
    ) : DeezerParse

    /** An error object (such as the quota limit, which Deezer sends with status 200) or a body that is not JSON. */
    data object Failed : DeezerParse
}

/** Reads `GET /search/artist` replies and decides which hit, if any, is the artist (pure; design 7.1, ADR 0029). */
object DeezerParser {
    /** The md5 of the empty string: the image hash Deezer uses for an artist without a photo. */
    const val EMPTY_IMAGE_HASH = "d41d8cd98f00b204e9800998ecf8427e"

    private const val EMPTY_HASH_PATH = "/images/artist//"

    fun isPlaceholder(url: String): Boolean =
        url.isBlank() || url.contains(EMPTY_HASH_PATH) || url.contains(EMPTY_IMAGE_HASH)

    @Suppress("SwallowedException")
    fun parse(body: String): DeezerParse =
        try {
            val root = Json.parseToJsonElement(body).jsonObject
            val data = root["data"]
            if ("error" in root || data == null) {
                DeezerParse.Failed
            } else {
                DeezerParse.Hits(data.jsonArray.mapNotNull { (it as? JsonObject)?.toHit() })
            }
        } catch (e: IllegalArgumentException) {
            // Not JSON, or JSON of another shape: a failed lookup, never a "not found".
            DeezerParse.Failed
        }

    /** The first hit whose name is the tag name, or `null`: a fuzzy top hit such as `Ed Sheeran` for `Ed` is wrong. */
    fun firstMatch(
        hits: List<DeezerHit>,
        tagName: String,
        matcher: ArtistNameMatcher,
    ): DeezerHit? = hits.firstOrNull { matcher.matches(tagName, it.name) }

    private fun JsonObject.toHit(): DeezerHit? {
        val name = this["name"]?.jsonPrimitive?.contentOrNull
        val picture = this["picture_medium"]?.jsonPrimitive?.contentOrNull
        return if (name == null || picture == null || isPlaceholder(picture)) null else DeezerHit(name, picture)
    }
}
