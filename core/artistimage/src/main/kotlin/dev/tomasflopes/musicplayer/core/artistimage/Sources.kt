package dev.tomasflopes.musicplayer.core.artistimage

/** The owner's choice for this artist, kept on the phone. It always wins (ADR 0029). */
class UserChoiceSource(
    private val store: ArtistImageOverrideStore,
    private val policy: OnlinePolicy,
) : ArtistImageSource {
    override val priority = ArtistImagePriority.USER_CHOICE

    override suspend fun find(artist: ArtistRef): SourceResult =
        when (val choice = store.get(artist.id)) {
            null -> SourceResult.Miss
            ArtistImageChoice.Generated -> SourceResult.SkipOnline
            is ArtistImageChoice.LocalFile -> SourceResult.Found(ArtistImage.LocalFile(choice.fileName))
            // A chosen photo is still an online one: with the setting Off it must not be downloaded.
            is ArtistImageChoice.Remote ->
                if (policy.allowed()) SourceResult.Found(ArtistImage.Remote(choice.url)) else SourceResult.SkipOnline
        }
}

/** How long a Deezer answer is trusted before the artist is looked up again. */
data class LookupTtl(
    val foundMs: Long = THIRTY_DAYS_MS,
    val notFoundMs: Long = SEVEN_DAYS_MS,
) {
    fun isFresh(
        lookup: ArtistImageLookup,
        nowMs: Long,
    ): Boolean {
        val trustedFor = if (lookup.pictureUrl != null) foundMs else notFoundMs
        return nowMs - lookup.checkedAtMs < trustedFor
    }

    private companion object {
        const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
        const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/**
 * Deezer's photo of the artist, when the name matches (design 7.1, ADR 0029). One request per artist per
 * [ttl]: a stored answer, "not found" included, is used instead. A failed request (offline, quota, bad reply)
 * is a miss that is not stored, so the next row appearance tries again.
 */
class DeezerSource(
    private val client: DeezerClient,
    private val cache: ArtistImageLookupCache,
    private val policy: OnlinePolicy,
    private val matcher: ArtistNameMatcher,
    private val time: TimeSource,
    private val ttl: LookupTtl = LookupTtl(),
) : ArtistImageSource {
    override val priority = ArtistImagePriority.DEEZER
    override val online = true

    override suspend fun find(artist: ArtistRef): SourceResult {
        val url = if (artist.name.isBlank() || !policy.allowed()) null else lookup(artist)
        return if (url == null) SourceResult.Miss else SourceResult.Found(ArtistImage.Remote(url))
    }

    private suspend fun lookup(artist: ArtistRef): String? {
        val now = time.nowMs()
        val stored = cache.get(artist.id)
        return if (stored != null && ttl.isFresh(stored, now)) stored.pictureUrl else fetch(artist, now)
    }

    private suspend fun fetch(
        artist: ArtistRef,
        now: Long,
    ): String? {
        val parsed = client.searchArtists(artist.name, SEARCH_LIMIT)?.let(DeezerParser::parse)
        val hits = (parsed as? DeezerParse.Hits)?.hits ?: return null
        val url = DeezerParser.firstMatch(hits, artist.name, matcher)?.pictureUrl
        cache.put(artist.id, ArtistImageLookup(url, now))
        return url
    }

    companion object {
        /** The top hit alone is wrong for short names, so a few are read and the first with the right name is used. */
        const val SEARCH_LIMIT = 5
    }
}

/** The covers of the artist's albums: one cover as it is, otherwise 2x2 (2 or 3 covers repeat in order). */
class AlbumCollageSource(
    private val covers: ArtistCovers,
) : ArtistImageSource {
    override val priority = ArtistImagePriority.ALBUM_COLLAGE

    override suspend fun find(artist: ArtistRef): SourceResult {
        val songs = covers.coverSongs(artist.id, COLLAGE_TILES)
        return when {
            songs.isEmpty() -> SourceResult.Miss
            songs.size == 1 -> SourceResult.Found(ArtistImage.Collage(songs))
            else -> SourceResult.Found(ArtistImage.Collage(List(COLLAGE_TILES) { songs[it % songs.size] }))
        }
    }
}

/** Letters on a tile: the end of the chain, it always has an answer. */
class InitialsSource : ArtistImageSource {
    override val priority = ArtistImagePriority.INITIALS

    override suspend fun find(artist: ArtistRef): SourceResult =
        SourceResult.Found(ArtistImage.Initials(initialsOf(artist.name)))
}
