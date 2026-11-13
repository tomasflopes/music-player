package dev.tomasflopes.musicplayer.core.model

/** A folder inside the music folder. [path] is relative to the volume; [parentPath] is null for the root. */
data class Folder(
    val path: String,
    val name: String,
    val parentPath: String?,
    val songCount: Int,
)
