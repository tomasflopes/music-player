package dev.tomasflopes.musicplayer.core.artistimage

import kotlin.coroutines.cancellation.CancellationException

/** Asks the sources in priority order; the first that finds an image wins (ADR 0029). */
class ArtistImageChain(
    sources: Collection<ArtistImageSource>,
) {
    private val ordered = sources.sortedBy { it.priority }

    init {
        val priorities = ordered.map { it.priority }
        require(priorities.size == priorities.toSet().size) { "Source priorities must be unique: $priorities" }
    }

    suspend fun resolve(artist: ArtistRef): ArtistImage {
        var onlineAllowed = true
        for (source in ordered) {
            if (onlineAllowed || !source.online) {
                when (val result = attempt(source, artist)) {
                    is SourceResult.Found -> return result.image
                    SourceResult.SkipOnline -> onlineAllowed = false
                    SourceResult.Miss -> Unit
                }
            }
        }
        return ArtistImage.Initials(initialsOf(artist.name))
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun attempt(
        source: ArtistImageSource,
        artist: ArtistRef,
    ): SourceResult =
        try {
            source.find(artist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A broken source must not leave an artist without a picture.
            SourceResult.Miss
        }
}
