package dev.tomasflopes.musicplayer.core.artistimage

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What every [ArtistImageSource] promises (ADR 0029); each implementation below runs it. */
abstract class ArtistImageSourceContract {
    abstract fun source(): ArtistImageSource

    @Test
    fun `answers the same twice`() =
        runTest {
            assertEquals(source().find(artist()), source().find(artist()))
        }

    @Test
    fun `never throws for an odd name`() =
        runTest {
            listOf("", "   ", "???", "A\$AP Rocky").forEach { source().find(artist(name = it)) }
        }

    @Test
    fun `a found image is never empty`() =
        runTest {
            when (val image = (source().find(artist()) as? SourceResult.Found)?.image) {
                is ArtistImage.Remote -> assertTrue(image.url.startsWith("https://"))
                is ArtistImage.LocalFile -> assertTrue(image.fileName.isNotBlank())
                is ArtistImage.Collage -> assertTrue(image.covers.isNotEmpty())
                is ArtistImage.Initials -> assertTrue(image.text.isNotEmpty())
                null -> Unit
            }
        }
}

private fun onlinePhotoSource() =
    FakeSource(ArtistImagePriority.DEEZER, online = true) { SourceResult.Found(ArtistImage.Remote(REAL_PHOTO)) }

class UserChoiceSourceTest : ArtistImageSourceContract() {
    private val store = FakeOverrideStore()
    private val policy = FakePolicy()

    override fun source(): ArtistImageSource = UserChoiceSource(store, policy)

    @Test
    fun `is local and ahead of every other source`() {
        assertEquals(false, source().online)
        assertTrue(source().priority < ArtistImagePriority.DEEZER)
    }

    @Test
    fun `no choice is a miss`() = runTest { assertEquals(SourceResult.Miss, source().find(artist())) }

    @Test
    fun `a gallery image is found`() =
        runTest {
            store.choices[artist().id] = ArtistImageChoice.LocalFile("1-5.img")
            assertEquals(SourceResult.Found(ArtistImage.LocalFile("1-5.img")), source().find(artist()))
        }

    @Test
    fun `a chosen online photo is found while online photos are allowed`() =
        runTest {
            store.choices[artist().id] = ArtistImageChoice.Remote(OTHER_PHOTO)
            assertEquals(SourceResult.Found(ArtistImage.Remote(OTHER_PHOTO)), source().find(artist()))
        }

    @Test
    fun `a chosen online photo is not used while online photos are off`() =
        runTest {
            store.choices[artist().id] = ArtistImageChoice.Remote(OTHER_PHOTO)
            policy.allowed = false
            assertEquals(SourceResult.SkipOnline, source().find(artist()))
        }

    @Test
    fun `generated skips the online sources`() =
        runTest {
            store.choices[artist().id] = ArtistImageChoice.Generated
            assertEquals(SourceResult.SkipOnline, source().find(artist()))
        }

    @Test
    fun `the choice beats Deezer in the chain`() =
        runTest {
            store.choices[artist().id] = ArtistImageChoice.LocalFile("mine.img")
            val deezer = onlinePhotoSource()
            val chain = ArtistImageChain(listOf(deezer, source(), InitialsSource()))
            assertEquals(ArtistImage.LocalFile("mine.img"), chain.resolve(artist()))
            assertEquals(0, deezer.calls)
        }

    @Test
    fun `generated keeps the album collage but not Deezer`() =
        runTest {
            store.choices[artist().id] = ArtistImageChoice.Generated
            val deezer = onlinePhotoSource()
            val collage = AlbumCollageSource { _, _ -> listOf(coverSong(1)) }
            val image = ArtistImageChain(listOf(deezer, source(), collage, InitialsSource())).resolve(artist())
            assertEquals(ArtistImage.Collage(listOf(coverSong(1))), image)
        }
}

class DeezerSourceTest : ArtistImageSourceContract() {
    private val client = FakeClient(deezerReply("Rui Veloso" to REAL_PHOTO))
    private val cache = FakeLookupCache()
    private val policy = FakePolicy()
    private val time = FakeTime()

    override fun source(): ArtistImageSource = DeezerSource(client, cache, policy, SameNameIgnoringCase, time)

    @Test
    fun `is online`() = assertEquals(true, source().online)

    @Test
    fun `a matching hit is found, asked with the name and a few results`() =
        runTest {
            assertEquals(SourceResult.Found(ArtistImage.Remote(REAL_PHOTO)), source().find(artist()))
            assertEquals(listOf("Rui Veloso" to DeezerSource.SEARCH_LIMIT), client.requests)
        }

    @Test
    fun `a wrong-name hit is a miss and is remembered as not found`() =
        runTest {
            client.body = deezerReply("Ed Sheeran" to REAL_PHOTO)
            assertEquals(SourceResult.Miss, source().find(artist(name = "Ed")))
            assertEquals(null, cache.entries[artist().id]?.pictureUrl)
            assertEquals(time.now, cache.entries[artist().id]?.checkedAtMs)
        }

    @Test
    fun `a placeholder photo is a miss`() =
        runTest {
            client.body = deezerReply("Rui Veloso" to PLACEHOLDER_PHOTO)
            assertEquals(SourceResult.Miss, source().find(artist()))
        }

    @Test
    fun `a fresh stored answer is used without a request, found or not`() =
        runTest {
            cache.entries[artist().id] = ArtistImageLookup(OTHER_PHOTO, time.now - 1000)
            assertEquals(SourceResult.Found(ArtistImage.Remote(OTHER_PHOTO)), source().find(artist()))
            cache.entries[artist(2).id] = ArtistImageLookup(null, time.now - 1000)
            assertEquals(SourceResult.Miss, source().find(artist(2)))
            assertEquals(emptyList<Pair<String, Int>>(), client.requests)
        }

    @Test
    fun `a not-found is asked again after a week, a found photo only after a month`() =
        runTest {
            val day = 24L * 60 * 60 * 1000
            cache.entries[artist(2).id] = ArtistImageLookup(null, time.now - 8 * day)
            source().find(artist(2))
            assertEquals(1, client.requests.size)

            cache.entries[artist(3).id] = ArtistImageLookup(OTHER_PHOTO, time.now - 29 * day)
            source().find(artist(3))
            assertEquals(1, client.requests.size)

            cache.entries[artist(4).id] = ArtistImageLookup(OTHER_PHOTO, time.now - 31 * day)
            source().find(artist(4))
            assertEquals(2, client.requests.size)
        }

    @Test
    fun `a failed request is a miss and is not remembered`() =
        runTest {
            client.body = null
            assertEquals(SourceResult.Miss, source().find(artist()))
            client.body = """{"error":{"code":4}}"""
            assertEquals(SourceResult.Miss, source().find(artist()))
            assertTrue(cache.entries.isEmpty())
        }

    @Test
    fun `off makes no request and does not even read the stored answer`() =
        runTest {
            policy.allowed = false
            cache.entries[artist().id] = ArtistImageLookup(OTHER_PHOTO, time.now)
            assertEquals(SourceResult.Miss, source().find(artist()))
            assertEquals(emptyList<Pair<String, Int>>(), client.requests)
        }

    @Test
    fun `a blank name makes no request`() =
        runTest {
            assertEquals(SourceResult.Miss, source().find(artist(name = " ")))
            assertEquals(emptyList<Pair<String, Int>>(), client.requests)
        }
}

class AlbumCollageSourceTest : ArtistImageSourceContract() {
    private var covers = listOf(coverSong(1), coverSong(2), coverSong(3), coverSong(4))

    override fun source(): ArtistImageSource = AlbumCollageSource { _, limit -> covers.take(limit) }

    @Test
    fun `no covers is a miss`() =
        runTest {
            covers = emptyList()
            assertEquals(SourceResult.Miss, source().find(artist()))
        }

    @Test
    fun `one album gives its single cover`() =
        runTest {
            covers = listOf(coverSong(1))
            assertEquals(SourceResult.Found(ArtistImage.Collage(listOf(coverSong(1)))), source().find(artist()))
        }

    @Test
    fun `four or more albums give a 2x2 of the first four`() =
        runTest {
            covers = (1L..6L).map(::coverSong)
            val image = (source().find(artist()) as SourceResult.Found).image as ArtistImage.Collage
            assertEquals((1L..4L).map(::coverSong), image.covers)
        }

    @Test
    fun `two or three albums repeat in order to fill the 2x2`() =
        runTest {
            covers = listOf(coverSong(1), coverSong(2))
            val two = (source().find(artist()) as SourceResult.Found).image as ArtistImage.Collage
            assertEquals(listOf(1L, 2L, 1L, 2L), two.covers.map { it.id.value })
            covers = listOf(coverSong(1), coverSong(2), coverSong(3))
            val three = (source().find(artist()) as SourceResult.Found).image as ArtistImage.Collage
            assertEquals(listOf(1L, 2L, 3L, 1L), three.covers.map { it.id.value })
        }
}

class InitialsSourceTest : ArtistImageSourceContract() {
    override fun source(): ArtistImageSource = InitialsSource()

    @Test
    fun `always finds letters and is the last source`() =
        runTest {
            assertEquals(SourceResult.Found(ArtistImage.Initials("RV")), source().find(artist()))
            assertEquals(SourceResult.Found(ArtistImage.Initials("?")), source().find(artist(name = "???")))
            assertEquals(ArtistImagePriority.INITIALS, source().priority)
        }
}
