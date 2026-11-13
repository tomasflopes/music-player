package dev.tomasflopes.musicplayer.ui.settings

import android.provider.MediaStore
import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot

/** The folder as the owner knows it: the volume, then the path below it ("Internal storage/Music"). */
fun MusicRoot.displayPath(): String {
    val volume =
        when (volumeName) {
            MediaStore.VOLUME_EXTERNAL_PRIMARY -> "Internal storage"
            else -> "Storage ${volumeName.uppercase()}"
        }
    val folder = relativePath.trimEnd('/')
    return if (folder.isEmpty()) volume else "$volume/$folder"
}
