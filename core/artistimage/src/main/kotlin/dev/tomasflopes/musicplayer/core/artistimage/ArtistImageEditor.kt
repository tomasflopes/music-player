package dev.tomasflopes.musicplayer.core.artistimage

import dev.tomasflopes.musicplayer.core.model.ArtistId

/**
 * The four things "Change photo" does (design 7.1): pick an online candidate, pick a gallery image, use the
 * generated image, go back to automatic. Each saves the choice, deletes the gallery copy it replaces and makes
 * the screens ask again.
 */
class ArtistImageEditor(
    private val store: ArtistImageOverrideStore,
    private val files: LocalImageFiles,
    private val resolver: ArtistImageResolver,
) {
    suspend fun current(id: ArtistId): ArtistImageChoice? = store.get(id)

    suspend fun useRemote(
        id: ArtistId,
        url: String,
    ) = replace(id, ArtistImageChoice.Remote(url))

    /** [fileName] is a copy that is already in app storage. */
    suspend fun useLocalFile(
        id: ArtistId,
        fileName: String,
    ) = replace(id, ArtistImageChoice.LocalFile(fileName))

    suspend fun useGenerated(id: ArtistId) = replace(id, ArtistImageChoice.Generated)

    suspend fun reset(id: ArtistId) {
        dropLocalCopy(id, keeping = null)
        store.clear(id)
        resolver.invalidate(id)
    }

    private suspend fun replace(
        id: ArtistId,
        choice: ArtistImageChoice,
    ) {
        dropLocalCopy(id, keeping = choice)
        store.set(id, choice)
        resolver.invalidate(id)
    }

    private suspend fun dropLocalCopy(
        id: ArtistId,
        keeping: ArtistImageChoice?,
    ) {
        val old = store.get(id) as? ArtistImageChoice.LocalFile
        if (old != null && old != keeping) files.delete(old.fileName)
    }
}
