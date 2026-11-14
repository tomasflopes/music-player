package dev.tomasflopes.musicplayer.core.artistimage

/** A Deezer artist the owner can pick the photo of. */
data class ArtistImageCandidate(
    val name: String,
    val pictureUrl: String,
)

sealed interface CandidateResult {
    /** The setting or the connection does not allow a request right now. */
    data object Disabled : CandidateResult

    /** The request failed; trying again later may work. */
    data object Unavailable : CandidateResult

    /** Up to [ArtistImageCandidates.LIMIT] artists with a real photo, best match first. Can be empty. */
    data class Found(
        val candidates: List<ArtistImageCandidate>,
    ) : CandidateResult
}

/** The Deezer artists for a name, for the "Change photo" picker (design 7.1); not name-filtered, the owner picks. */
class ArtistImageCandidates(
    private val client: DeezerClient,
    private val policy: OnlinePolicy,
) {
    suspend fun find(name: String): CandidateResult =
        if (name.isBlank() || !policy.allowed()) CandidateResult.Disabled else search(name)

    private suspend fun search(name: String): CandidateResult =
        when (val parsed = client.searchArtists(name, LIMIT)?.let(DeezerParser::parse)) {
            null, DeezerParse.Failed -> CandidateResult.Unavailable
            is DeezerParse.Hits ->
                CandidateResult.Found(
                    parsed.hits
                        .distinctBy { it.pictureUrl }
                        .take(LIMIT)
                        .map { ArtistImageCandidate(it.name, it.pictureUrl) },
                )
        }

    companion object {
        const val LIMIT = 5
    }
}
