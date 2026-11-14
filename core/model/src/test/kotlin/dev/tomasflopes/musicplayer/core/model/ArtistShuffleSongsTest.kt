package dev.tomasflopes.musicplayer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtistShuffleSongsTest {
    private val own1 = song("a.mp3", id = 1)
    private val own2 = song("b.mp3", id = 2)
    private val feat1 = song("c.mp3", id = 3)
    private val feat2 = song("d.mp3", id = 4)

    @Test
    fun `own songs come first, then the featured ones`() {
        assertEquals(listOf(own1, own2, feat1, feat2), artistShuffleSongs(listOf(own1, own2), listOf(feat1, feat2)))
    }

    @Test
    fun `a song that is both own and featured is queued once`() {
        assertEquals(listOf(own1, feat1), artistShuffleSongs(listOf(own1), listOf(own1, feat1)))
    }

    @Test
    fun `an artist with no featurings shuffles the own songs only`() {
        assertEquals(listOf(own1, own2), artistShuffleSongs(listOf(own1, own2), emptyList()))
    }

    @Test
    fun `an artist with only featurings still has songs`() {
        assertEquals(listOf(feat1), artistShuffleSongs(emptyList(), listOf(feat1)))
    }
}
