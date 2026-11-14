package dev.tomasflopes.musicplayer.core.artistimage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ArtistImageResolverTest {
    private var answer: ArtistImage = ArtistImage.Remote(REAL_PHOTO)
    private val source = FakeSource(1) { SourceResult.Found(answer) }

    private fun resolver(
        scope: TestScope,
        maxEntries: Int = 100,
    ) = ArtistImageResolver(ArtistImageChain(listOf(source)), scope.backgroundScope, maxEntries)

    @Test
    fun `an answer is kept, so scrolling back does not ask again`() =
        runTest {
            val resolver = resolver(this)
            resolver.resolve(artist())
            resolver.resolve(artist())
            assertEquals(1, source.calls)
            assertEquals(ArtistImage.Remote(REAL_PHOTO), resolver.peek(artist().id))
        }

    @Test
    fun `rows asking for the same artist at once share one lookup`() =
        runTest {
            val resolver = resolver(this)
            val results = (1..5).map { async { resolver.resolve(artist()) } }.awaitAll()
            assertEquals(1, source.calls)
            assertEquals(1, results.toSet().size)
        }

    @Test
    fun `invalidating one artist asks again for that artist only and ticks changes`() =
        runTest {
            val resolver = resolver(this)
            resolver.resolve(artist(1))
            resolver.resolve(artist(2))
            val before = resolver.changes.value
            answer = ArtistImage.Remote(OTHER_PHOTO)
            resolver.invalidate(artist(1).id)
            assertNull(resolver.peek(artist(1).id))
            assertEquals(ArtistImage.Remote(OTHER_PHOTO), resolver.resolve(artist(1)))
            assertEquals(ArtistImage.Remote(REAL_PHOTO), resolver.peek(artist(2).id))
            assertEquals(before + 1, resolver.changes.value)
        }

    @Test
    fun `invalidating everything forgets every answer`() =
        runTest {
            val resolver = resolver(this)
            resolver.resolve(artist(1))
            resolver.resolve(artist(2))
            resolver.invalidateAll()
            assertNull(resolver.peek(artist(1).id))
            assertNull(resolver.peek(artist(2).id))
        }

    @Test
    fun `an answer that was in flight when it was invalidated is not kept`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val slow =
                object : ArtistImageSource {
                    override val priority = 1

                    override suspend fun find(artist: ArtistRef): SourceResult {
                        gate.await()
                        return SourceResult.Found(answer)
                    }
                }
            val resolver = ArtistImageResolver(ArtistImageChain(listOf(slow)), backgroundScope)
            val pending = async { resolver.resolve(artist()) }
            runCurrent()
            resolver.invalidate(artist().id)
            gate.complete(Unit)
            pending.await()
            assertNull(resolver.peek(artist().id))
        }

    @Test
    fun `the least recently used answer is dropped past the limit`() =
        runTest {
            val resolver = resolver(this, maxEntries = 2)
            resolver.resolve(artist(1))
            resolver.resolve(artist(2))
            resolver.resolve(artist(3))
            assertNull(resolver.peek(artist(1).id))
            assertEquals(ArtistImage.Remote(REAL_PHOTO), resolver.peek(artist(3).id))
        }
}
