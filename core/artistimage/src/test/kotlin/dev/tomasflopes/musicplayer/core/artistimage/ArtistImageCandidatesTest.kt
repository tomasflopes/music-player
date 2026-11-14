package dev.tomasflopes.musicplayer.core.artistimage

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtistImageCandidatesTest {
    private val client = FakeClient()
    private val policy = FakePolicy()
    private val candidates = ArtistImageCandidates(client, policy)

    @Test
    fun `lists the artists with a real photo, best match first, without the placeholder`() =
        runTest {
            client.body = deezerReply("Ed" to PLACEHOLDER_PHOTO, "Ed Sheeran" to REAL_PHOTO, "Eddie" to OTHER_PHOTO)
            val found = candidates.find("Ed") as CandidateResult.Found
            assertEquals(listOf("Ed Sheeran", "Eddie"), found.candidates.map { it.name })
            assertEquals(listOf("Ed" to ArtistImageCandidates.LIMIT), client.requests)
        }

    @Test
    fun `shows at most five and never the same photo twice`() =
        runTest {
            val photos = (1..8).map { REAL_PHOTO.replace("0123", "000$it") }
            val others = photos.drop(1).map { "B" to it }
            client.body = deezerReply("A" to photos[0], "A2" to photos[0], *others.toTypedArray())
            val found = candidates.find("A") as CandidateResult.Found
            assertEquals(5, found.candidates.size)
            val urls = found.candidates.map { it.pictureUrl }
            assertEquals(5, urls.toSet().size)
        }

    @Test
    fun `no photo anywhere is an empty list`() =
        runTest {
            client.body = deezerReply("Ed" to PLACEHOLDER_PHOTO)
            assertEquals(CandidateResult.Found(emptyList()), candidates.find("Ed"))
        }

    @Test
    fun `a failed request is unavailable`() =
        runTest {
            assertEquals(CandidateResult.Unavailable, candidates.find("Ed"))
            client.body = """{"error":{"code":4}}"""
            assertEquals(CandidateResult.Unavailable, candidates.find("Ed"))
        }

    @Test
    fun `off makes no request`() =
        runTest {
            policy.allowed = false
            assertEquals(CandidateResult.Disabled, candidates.find("Ed"))
            assertEquals(emptyList<Pair<String, Int>>(), client.requests)
        }
}
