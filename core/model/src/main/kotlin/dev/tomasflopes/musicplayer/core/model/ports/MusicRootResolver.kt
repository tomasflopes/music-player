package dev.tomasflopes.musicplayer.core.model.ports

/**
 * Turns what the system folder picker returned into the [MusicRoot] the app stores (ADR 0005).
 * Implemented by `:data`, which knows how MediaStore lists folders.
 */
interface MusicRootResolver {
    /** `null` when the folder is not on a volume MediaStore lists; the caller asks the owner to pick again. */
    fun resolve(
        treeUri: String,
        treeDocumentId: String,
    ): MusicRoot?
}
