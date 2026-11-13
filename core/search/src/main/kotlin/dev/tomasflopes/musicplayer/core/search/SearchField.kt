package dev.tomasflopes.musicplayer.core.search

/**
 * A field the search index covers. The declaration order is the tie-break order: a title match beats the
 * same tier of match in a less specific field.
 */
enum class SearchField {
    TITLE,
    FEATURED,
    ARTIST,
    ALBUM_ARTIST,
    ALBUM,
    FOLDER,
}
