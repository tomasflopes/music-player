package dev.tomasflopes.musicplayer.core.artistimage

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ArtistImageChainTest {
    private val photo = ArtistImage.Remote(REAL_PHOTO)

    private fun found(image: ArtistImage) = { SourceResult.Found(image) }

    @Test
    fun `lower priority is asked first whatever the order they were registered in`() =
        runTest {
            val late = FakeSource(200, result = found(ArtistImage.Initials("L")))
            val early = FakeSource(100, result = found(photo))
            assertEquals(photo, ArtistImageChain(listOf(late, early)).resolve(artist()))
            assertEquals(0, late.calls)
        }

    @Test
    fun `a miss falls through to the next source`() =
        runTest {
            val miss = FakeSource(1)
            val hit = FakeSource(2, result = found(photo))
            assertEquals(photo, ArtistImageChain(setOf(miss, hit)).resolve(artist()))
            assertEquals(1, miss.calls)
        }

    @Test
    fun `skip online skips online sources only`() =
        runTest {
            val choice = FakeSource(1) { SourceResult.SkipOnline }
            val online = FakeSource(2, online = true, result = found(photo))
            val collage = FakeSource(3, result = found(ArtistImage.Initials("RV")))
            val image = ArtistImageChain(listOf(choice, online, collage)).resolve(artist())
            assertEquals(ArtistImage.Initials("RV"), image)
            assertEquals(0, online.calls)
        }

    @Test
    fun `a source that throws counts as a miss`() =
        runTest {
            val broken = FakeSource(1) { error("boom") }
            val hit = FakeSource(2, result = found(photo))
            assertEquals(photo, ArtistImageChain(listOf(broken, hit)).resolve(artist()))
        }

    @Test
    fun `when every source misses the artist still gets initials`() =
        runTest {
            val image = ArtistImageChain(listOf(FakeSource(1))).resolve(artist(name = "Rui Veloso"))
            assertEquals(ArtistImage.Initials("RV"), image)
        }

    @Test
    fun `two sources with the same priority are refused`() {
        assertThrows(IllegalArgumentException::class.java) { ArtistImageChain(listOf(FakeSource(1), FakeSource(1))) }
    }
}
