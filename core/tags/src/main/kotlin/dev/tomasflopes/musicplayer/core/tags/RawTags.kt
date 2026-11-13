package dev.tomasflopes.musicplayer.core.tags

/** Which kind of tag the values came from. */
enum class TagVersion { ID3V2_2, ID3V2_3, ID3V2_4, ID3V1, NONE }

/**
 * What the file's tag says, exactly as tagged. `null` means the frame is missing or empty; nothing is guessed
 * here (no "Unknown artist", no filename fallback). A track or disc number of 0 counts as not tagged.
 */
data class RawTags(
    val title: String?,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val trackNumber: Int?,
    val trackTotal: Int?,
    val discNumber: Int?,
    val discTotal: Int?,
    val hasCover: Boolean,
    val version: TagVersion,
)
