package dev.tomasflopes.musicplayer.data.scan

import dev.tomasflopes.musicplayer.core.model.ports.MusicRoot
import dev.tomasflopes.musicplayer.core.model.ports.MusicRootResolver

/** [MusicRootResolver] over [TreeUriMapper]: the picked folder becomes a MediaStore volume and a path prefix. */
class TreeUriMusicRootResolver : MusicRootResolver {
    override fun resolve(
        treeUri: String,
        treeDocumentId: String,
    ): MusicRoot? =
        TreeUriMapper.fromTreeDocumentId(treeDocumentId)?.let { location ->
            MusicRoot(treeUri, location.volumeName, location.relativePath)
        }
}
